#include "gaussian_blur.h"

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES3/gl3.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

#define TAG  "GPUBlur"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/**
 * Maximum number of texture samples on each side of the center pixel for a
 * single shader pass. Radius values larger than this are handled by
 * downscaling the image before blurring, then upscaling the result, which
 * keeps the shader fast regardless of how big the requested radius is.
 */
static constexpr int MAX_HALF_KERNEL = 64;

// The uniform array in the shader is sized MAX_HALF_KERNEL + 1 (center slot
// + one slot per side sample). Must stay in sync with the GLSL declarations.
static constexpr int WEIGHT_ARRAY_SIZE = MAX_HALF_KERNEL + 1; // 65

// ---------------------------------------------------------------------------
// GLSL shader sources
// ---------------------------------------------------------------------------

/**
 * Shared vertex shader. Attribute layout locations are hard-coded so we can
 * set up a single VAO and reuse it across all three programs.
 *
 * The fullscreen quad maps clip-space (−1,−1)→(1,1) to texcoords (0,0)→(1,1).
 * This means GL texcoord Y=0 (bottom of texture in OpenGL convention) lands on
 * the bottom of the screen. Because glReadPixels starts reading from Y=0 as
 * well, the readback naturally comes out in the top-to-bottom order that
 * Android bitmaps expect — no manual row flip is needed.
 */
static const char *VERT_SRC = R"glsl(
#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord   = aTexCoord;
}
)glsl";

/**
 * Horizontal Gaussian blur pass.
 *
 * Walks along the X axis collecting weighted color samples. The loop bound is
 * kept as a compile-time constant (65) with an early break so drivers that
 * struggle with non-constant loop bounds still behave correctly.
 */
static const char *HBLUR_FRAG_SRC = R"glsl(
#version 300 es
precision mediump float;
uniform sampler2D uTexture;
uniform float     uWeights[65];
uniform int       uKernelRadius;
uniform float     uTexelWidth;
in  vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec4 result = texture(uTexture, vTexCoord) * uWeights[0];
    for (int i = 1; i < 65; i++) {
        if (i > uKernelRadius) break;
        float off = float(i) * uTexelWidth;
        result += texture(uTexture, vTexCoord + vec2( off, 0.0)) * uWeights[i];
        result += texture(uTexture, vTexCoord + vec2(-off, 0.0)) * uWeights[i];
    }
    fragColor = result;
}
)glsl";

/**
 * Vertical Gaussian blur pass — same logic as horizontal, but sampling
 * along the Y axis instead.
 */
static const char *VBLUR_FRAG_SRC = R"glsl(
#version 300 es
precision mediump float;
uniform sampler2D uTexture;
uniform float     uWeights[65];
uniform int       uKernelRadius;
uniform float     uTexelHeight;
in  vec2 vTexCoord;
out vec4 fragColor;
void main() {
    vec4 result = texture(uTexture, vTexCoord) * uWeights[0];
    for (int i = 1; i < 65; i++) {
        if (i > uKernelRadius) break;
        float off = float(i) * uTexelHeight;
        result += texture(uTexture, vTexCoord + vec2(0.0,  off)) * uWeights[i];
        result += texture(uTexture, vTexCoord + vec2(0.0, -off)) * uWeights[i];
    }
    fragColor = result;
}
)glsl";

/**
 * Passthrough shader used for the optional downsample / upsample steps when
 * the requested radius exceeds MAX_HALF_KERNEL. GL_LINEAR filtering on the
 * source texture handles the bilinear interpolation automatically.
 */
static const char *COPY_FRAG_SRC = R"glsl(
#version 300 es
precision mediump float;
uniform sampler2D uTexture;
in  vec2 vTexCoord;
out vec4 fragColor;
void main() {
    fragColor = texture(uTexture, vTexCoord);
}
)glsl";

// The six vertices of the fullscreen quad (two triangles).
// Position (X, Y) and texcoord (U, V) are interleaved per vertex.
// Texcoord Y=0 is at the bottom of the screen per the OpenGL convention.
static const float QUAD_VERTS[] = {
        -1.0f, -1.0f, 0.0f, 0.0f,
        1.0f, -1.0f, 1.0f, 0.0f,
        1.0f, 1.0f, 1.0f, 1.0f,
        -1.0f, -1.0f, 0.0f, 0.0f,
        1.0f, 1.0f, 1.0f, 1.0f,
        -1.0f, 1.0f, 0.0f, 1.0f,
};

// ---------------------------------------------------------------------------
// RAII guards — keep GL and EGL state tidy on every exit path
// ---------------------------------------------------------------------------

/**
 * Owns all the GL objects allocated during one blur call. Everything is
 * destroyed in the destructor so early returns do not cause leaks.
 * The destructor must run while the EGL context is still current, which is
 * guaranteed because GlResources is declared after EglGuard in the caller.
 */
struct GlResources {
    GLuint hblurProg = 0, vblurProg = 0, copyProg = 0;
    GLuint vao = 0, vbo = 0;
    GLuint srcTex = 0;
    GLuint downTex = 0, downFbo = 0;
    GLuint tmpTex = 0, tmpFbo = 0;
    GLuint blurTex = 0, blurFbo = 0;
    GLuint upTex = 0, upFbo = 0;

    ~GlResources() {
        if (vao) {
            glDeleteVertexArrays(1, &vao);
            glDeleteBuffers(1, &vbo);
        }
        if (srcTex) glDeleteTextures(1, &srcTex);
        if (downTex) {
            glDeleteTextures(1, &downTex);
            glDeleteFramebuffers(1, &downFbo);
        }
        if (tmpTex) {
            glDeleteTextures(1, &tmpTex);
            glDeleteFramebuffers(1, &tmpFbo);
        }
        if (blurTex) {
            glDeleteTextures(1, &blurTex);
            glDeleteFramebuffers(1, &blurFbo);
        }
        if (upTex) {
            glDeleteTextures(1, &upTex);
            glDeleteFramebuffers(1, &upFbo);
        }
        if (hblurProg) glDeleteProgram(hblurProg);
        if (vblurProg) glDeleteProgram(vblurProg);
        if (copyProg) glDeleteProgram(copyProg);
    }
};

/**
 * Owns the EGL display, context, and pbuffer surface for one blur call.
 * Also saves and restores any context that was current on the calling thread
 * so we do not accidentally clobber an existing rendering setup.
 */
struct EglGuard {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;
    bool didInitEgl = false;

    // Previous thread-local EGL state to restore on teardown
    EGLDisplay prevDisplay = EGL_NO_DISPLAY;
    EGLContext prevContext = EGL_NO_CONTEXT;
    EGLSurface prevDrawSurf = EGL_NO_SURFACE;
    EGLSurface prevReadSurf = EGL_NO_SURFACE;

    ~EglGuard() {
        if (display == EGL_NO_DISPLAY) return;

        // Release our context from this thread before destroying it
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);

        // Only terminate the display if we were the one who initialized it.
        // Calling eglTerminate on a display that another part of the app is
        // still using would silently destroy all its contexts and surfaces.
        if (didInitEgl) eglTerminate(display);

        // Restore whatever context was current before we started
        if (prevContext != EGL_NO_CONTEXT && prevDisplay != EGL_NO_DISPLAY) {
            eglMakeCurrent(prevDisplay, prevDrawSurf, prevReadSurf, prevContext);
        }
    }
};

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

/** Compiles a single shader stage and returns its handle, or 0 on failure. */
static GLuint compileShader(GLenum type, const char *src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);

    GLint status = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        char log[1024];
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        LOGE("Shader compile error: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

/** Links a vertex + fragment shader into a program. Returns 0 on failure. */
static GLuint createProgram(const char *vertSrc, const char *fragSrc) {
    GLuint vert = compileShader(GL_VERTEX_SHADER, vertSrc);
    if (!vert) return 0;
    GLuint frag = compileShader(GL_FRAGMENT_SHADER, fragSrc);
    if (!frag) {
        glDeleteShader(vert);
        return 0;
    }

    GLuint prog = glCreateProgram();
    glAttachShader(prog, vert);
    glAttachShader(prog, frag);
    glLinkProgram(prog);
    glDeleteShader(vert);
    glDeleteShader(frag);

    GLint status = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &status);
    if (!status) {
        char log[1024];
        glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
        LOGE("Program link error: %s", log);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
}

/**
 * Computes the normalized half-Gaussian kernel for a given radius.
 *
 * Only the center weight and one side of the symmetric kernel are stored
 * (weights[0] = center, weights[i] = weight for samples i pixels away).
 * The returned kernel radius is capped at MAX_HALF_KERNEL — callers are
 * expected to downsample the image when the requested radius is larger.
 */
static void computeGaussianWeights(float radius,
                                   float weights[WEIGHT_ARRAY_SIZE],
                                   int *outKernelRadius) {
    // sigma = radius / 3 means 99.7% of the Gaussian distribution fits
    // within the kernel, so edge weights are negligible.
    float sigma = radius / 3.0f;
    if (sigma < 0.001f) sigma = 0.001f;

    int kr = static_cast<int>(std::ceil(radius));
    if (kr > MAX_HALF_KERNEL) kr = MAX_HALF_KERNEL;
    *outKernelRadius = kr;

    float sum = 0.0f;
    for (int i = 0; i <= kr; i++) {
        float x = static_cast<float>(i);
        weights[i] = std::exp(-(x * x) / (2.0f * sigma * sigma));
        sum += (i == 0) ? weights[i] : 2.0f * weights[i];
    }
    // Normalize so all weights sum to 1, keeping brightness unchanged
    for (int i = 0; i <= kr; i++) weights[i] /= sum;
}

/**
 * Creates an RGBA8 texture of the given size and a framebuffer that renders
 * into it. Returns false if the framebuffer turns out to be incomplete, which
 * usually means the GPU ran out of memory.
 */
static bool makeTexFbo(int w, int h, GLuint *tex, GLuint *fbo) {
    glGenTextures(1, tex);
    glBindTexture(GL_TEXTURE_2D, *tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenFramebuffers(1, fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, *fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, *tex, 0);

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("Framebuffer incomplete for size %d x %d", w, h);
        return false;
    }
    return true;
}

// ---------------------------------------------------------------------------
// Public entry point
// ---------------------------------------------------------------------------

uint8_t *gaussianBlurGpu(const uint8_t *pixels, int width, int height, float radius) {
    if (radius < 1.0f) radius = 1.0f;

    // EglGuard must be declared before GlResources so that its destructor
    // fires AFTER the GL objects have already been cleaned up. Destructors
    // run in reverse declaration order, which is exactly what we want here.
    EglGuard egl;
    GlResources gl;

    // --- EGL setup ---

    // Save whatever context this thread had so we can put it back afterward.
    egl.prevDisplay = eglGetCurrentDisplay();
    egl.prevContext = eglGetCurrentContext();
    egl.prevDrawSurf = eglGetCurrentSurface(EGL_DRAW);
    egl.prevReadSurf = eglGetCurrentSurface(EGL_READ);

    egl.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (egl.display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return nullptr;
    }

    // eglInitialize is safe to call even when another part of the app has
    // already done so — we track whether we were the first caller so we only
    // call eglTerminate if we truly owned the initialization.
    EGLint major = 0, minor = 0;
    if (eglInitialize(egl.display, &major, &minor)) {
        egl.didInitEgl = true;
    }

#ifndef EGL_OPENGL_ES3_BIT
#define EGL_OPENGL_ES3_BIT 0x00000040
#endif

    EGLint configAttribs[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_NONE
    };

    EGLConfig config = nullptr;
    EGLint numConfig = 0;
    if (!eglChooseConfig(egl.display, configAttribs, &config, 1, &numConfig)
        || numConfig < 1) {
        LOGE("No suitable EGL config for OpenGL ES 3");
        return nullptr;
    }

    // A 1×1 pbuffer is enough because all actual rendering goes to FBOs.
    EGLint pbufAttribs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    egl.surface = eglCreatePbufferSurface(egl.display, config, pbufAttribs);
    if (egl.surface == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed: 0x%x", eglGetError());
        return nullptr;
    }

    EGLint ctxAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    egl.context = eglCreateContext(egl.display, config,
                                   EGL_NO_CONTEXT, ctxAttribs);
    if (egl.context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        return nullptr;
    }

    if (!eglMakeCurrent(egl.display, egl.surface, egl.surface, egl.context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
        return nullptr;
    }

    // --- Build shader programs ---

    gl.hblurProg = createProgram(VERT_SRC, HBLUR_FRAG_SRC);
    gl.vblurProg = createProgram(VERT_SRC, VBLUR_FRAG_SRC);
    gl.copyProg = createProgram(VERT_SRC, COPY_FRAG_SRC);
    if (!gl.hblurProg || !gl.vblurProg || !gl.copyProg) {
        LOGE("Shader compilation failed");
        return nullptr;
    }

    // --- Fullscreen quad (shared VAO for all passes) ---

    glGenVertexArrays(1, &gl.vao);
    glGenBuffers(1, &gl.vbo);
    glBindVertexArray(gl.vao);
    glBindBuffer(GL_ARRAY_BUFFER, gl.vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(QUAD_VERTS), QUAD_VERTS, GL_STATIC_DRAW);
    // location 0 = aPosition (2 floats), location 1 = aTexCoord (2 floats)
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), reinterpret_cast<void *>(0));
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), reinterpret_cast<void *>(2 * sizeof(float)));
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);

    // --- Upload source pixels to a GL texture ---

    glGenTextures(1, &gl.srcTex);
    glBindTexture(GL_TEXTURE_2D, gl.srcTex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // --- Decide working resolution ---
    //
    // When the caller asks for a radius larger than MAX_HALF_KERNEL we cannot
    // satisfy it in a single shader pass without reading hundreds of texels per
    // fragment. Instead we scale the image down so the same effective blur can
    // be achieved with a kernel of exactly MAX_HALF_KERNEL, then scale back up.
    // The bilinear filtering during up/downscale is handled for free by the GPU.
    float effectiveRadius = radius;
    int workW = width;
    int workH = height;

    if (radius > static_cast<float>(MAX_HALF_KERNEL)) {
        float scale = static_cast<float>(MAX_HALF_KERNEL) / radius;
        workW = std::max(1, static_cast<int>(static_cast<float>(width) * scale));
        workH = std::max(1, static_cast<int>(static_cast<float>(height) * scale));
        effectiveRadius = static_cast<float>(MAX_HALF_KERNEL);
    }

    GLuint blurInputTex = gl.srcTex;

    // Downsample pass (only when the radius demands it)
    if (workW != width || workH != height) {
        if (!makeTexFbo(workW, workH, &gl.downTex, &gl.downFbo)) return nullptr;
        glViewport(0, 0, workW, workH);
        glUseProgram(gl.copyProg);
        glUniform1i(glGetUniformLocation(gl.copyProg, "uTexture"), 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gl.srcTex);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        blurInputTex = gl.downTex;
    }

    // --- Compute Gaussian weights ---

    float weights[WEIGHT_ARRAY_SIZE] = {};
    int kernelRadius = 0;
    computeGaussianWeights(effectiveRadius, weights, &kernelRadius);

    // --- Horizontal blur pass ---

    if (!makeTexFbo(workW, workH, &gl.tmpTex, &gl.tmpFbo)) return nullptr;
    glBindFramebuffer(GL_FRAMEBUFFER, gl.tmpFbo);
    glViewport(0, 0, workW, workH);
    glUseProgram(gl.hblurProg);
    glUniform1i(glGetUniformLocation(gl.hblurProg, "uTexture"), 0);
    glUniform1fv(glGetUniformLocation(gl.hblurProg, "uWeights"), WEIGHT_ARRAY_SIZE, weights);
    glUniform1i(glGetUniformLocation(gl.hblurProg, "uKernelRadius"), kernelRadius);
    glUniform1f(glGetUniformLocation(gl.hblurProg, "uTexelWidth"),
                1.0f / static_cast<float>(workW));
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, blurInputTex);
    glDrawArrays(GL_TRIANGLES, 0, 6);

    // --- Vertical blur pass ---

    if (!makeTexFbo(workW, workH, &gl.blurTex, &gl.blurFbo)) return nullptr;
    glBindFramebuffer(GL_FRAMEBUFFER, gl.blurFbo);
    glUseProgram(gl.vblurProg);
    glUniform1i(glGetUniformLocation(gl.vblurProg, "uTexture"), 0);
    glUniform1fv(glGetUniformLocation(gl.vblurProg, "uWeights"), WEIGHT_ARRAY_SIZE, weights);
    glUniform1i(glGetUniformLocation(gl.vblurProg, "uKernelRadius"), kernelRadius);
    glUniform1f(glGetUniformLocation(gl.vblurProg, "uTexelHeight"),
                1.0f / static_cast<float>(workH));
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, gl.tmpTex);
    glDrawArrays(GL_TRIANGLES, 0, 6);

    // --- Upsample pass (only when we downscaled earlier) ---

    GLuint readFbo = gl.blurFbo;
    int readW = workW;
    int readH = workH;

    if (workW != width || workH != height) {
        if (!makeTexFbo(width, height, &gl.upTex, &gl.upFbo)) return nullptr;
        glBindFramebuffer(GL_FRAMEBUFFER, gl.upFbo);
        glViewport(0, 0, width, height);
        glUseProgram(gl.copyProg);
        glUniform1i(glGetUniformLocation(gl.copyProg, "uTexture"), 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gl.blurTex);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        readFbo = gl.upFbo;
        readW = width;
        readH = height;
    }

    // --- Read the result back to CPU memory ---
    //
    // glReadPixels delivers rows from Y=0 (bottom of the framebuffer) upward.
    // Because we uploaded the Android bitmap so that its first row landed at
    // GL texture Y=0 (which the quad maps to screen Y=−1, i.e., the bottom),
    // the bottom of the framebuffer holds the first row of the original image.
    // That means the readback order already matches Android's top-to-bottom
    // layout, so we do not need to flip rows afterward.
    auto *result = new uint8_t[static_cast<size_t>(readW) * readH * 4];
    glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
    glReadPixels(0, 0, readW, readH, GL_RGBA, GL_UNSIGNED_BYTE, result);

    // GlResources destructor fires here (while EGL context is still current),
    // then EglGuard destructor tears down the EGL state.
    return result;
}

