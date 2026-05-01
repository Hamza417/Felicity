cmake_minimum_required(VERSION 3.22.1)
project("felicity_milkdrop" LANGUAGES C CXX)

include(FetchContent)

# ---------------------------------------------------------------------------
# projectM configuration
# ---------------------------------------------------------------------------
set(ENABLE_GLES            ON  CACHE BOOL "Build with OpenGL ES instead of OpenGL" FORCE)
set(BUILD_TESTING          OFF CACHE BOOL "Disable CTest suite"                FORCE)
set(PROJECTM_BUILD_TESTS   OFF CACHE BOOL "Disable projectM unit tests"        FORCE)
set(PROJECTM_BUILD_SDLUI   OFF CACHE BOOL "Disable SDL2 standalone frontend"   FORCE)
set(ENABLE_LLVM            OFF CACHE BOOL "Disable LLVM JIT expression engine" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "Static build for JNI bundling"      FORCE)
set(PROJECTM_USE_SYSTEM_GLM OFF CACHE BOOL "Use projectM-bundled GLM" FORCE)

FetchContent_Declare(projectm
    GIT_REPOSITORY     https://github.com/projectM-visualizer/projectm.git
    GIT_TAG            v4.1.6
    GIT_SHALLOW        FALSE
    GIT_SUBMODULES_RECURSE TRUE
)

# ---------------------------------------------------------------------------
# REPRODUCIBILITY BLOCK
# MUST BE DECLARED BEFORE MakeAvailable!
# ---------------------------------------------------------------------------

# Use add_compile_options so third-party targets are FORCED to inherit them.
add_compile_options(
    "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=."
    "-ffile-prefix-map=${CMAKE_BINARY_DIR}=./build"
    "-ffile-prefix-map=${ANDROID_NDK}=./ndk"
    "-Werror=date-time"
)

# Archive rules remain globally scoped variables
set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")

# ---------------------------------------------------------------------------
# NOW we build projectM, inheriting the flags above
# ---------------------------------------------------------------------------
FetchContent_MakeAvailable(projectm)

# ---------------------------------------------------------------------------
# Felicity milkdrop JNI bridge
# ---------------------------------------------------------------------------
add_library(felicity_milkdrop SHARED
    milkdrop-bridge.cpp)

set_target_properties(felicity_milkdrop PROPERTIES
    CXX_STANDARD          17
    CXX_STANDARD_REQUIRED ON)

target_include_directories(felicity_milkdrop PRIVATE
    ${projectm_SOURCE_DIR}/src/libprojectM
    ${projectm_SOURCE_DIR}/src/api/include)

find_library(log-lib  log)
find_library(gles-lib GLESv3)
find_library(egl-lib  EGL)

target_link_libraries(felicity_milkdrop
    PRIVATE
    projectM
    ${log-lib}
    ${gles-lib}
    ${egl-lib})

# ---------------------------------------------------------------------------
# Force deterministic linker hash for the final shared library
# ---------------------------------------------------------------------------
target_link_options(felicity_milkdrop
    PRIVATE
    "-Wl,--build-id=none"
)

set(CMAKE_DEBUG_POSTFIX "")
