cmake_minimum_required(VERSION 3.22.1)
project("felicity_gpu_blur" LANGUAGES CXX)

# ---------------------------------------------------------------------------
# REPRODUCIBILITY BLOCK
# ---------------------------------------------------------------------------

# Strip paths for both YOUR source AND FetchContent's downloaded source
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")

# Strip Android NDK paths
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${ANDROID_NDK}=./ndk")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${ANDROID_NDK}=./ndk")

add_library(felicity_gpu_blur SHARED
        gaussian_blur.cpp
        blur_jni.cpp
)

target_compile_features(felicity_gpu_blur PRIVATE cxx_std_17)

# Link the system libraries that give us EGL, OpenGL ES 3, Android bitmap
# access, and logging. All of these ship with every Android device.
find_library(log-lib log)
find_library(android-lib android)
find_library(EGL-lib EGL)
find_library(GLESv3-lib GLESv3)
find_library(jni-lib jnigraphics)

target_link_libraries(felicity_gpu_blur
        ${log-lib}
        ${android-lib}
        ${EGL-lib}
        ${GLESv3-lib}
        ${jni-lib}
)

