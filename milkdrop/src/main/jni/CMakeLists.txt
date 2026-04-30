cmake_minimum_required(VERSION 3.22.1)
project("felicity_milkdrop" LANGUAGES C CXX)

include(FetchContent)

# ---------------------------------------------------------------------------
# projectM configuration — must be set BEFORE FetchContent_MakeAvailable so
# that the projectM CMakeLists picks them up via cache variables.
# ---------------------------------------------------------------------------

# Use OpenGL ES instead of desktop OpenGL (mandatory on Android)
set(ENABLE_GLES           ON  CACHE BOOL "Build with OpenGL ES instead of OpenGL" FORCE)

# Disable all host-only or optional components
set(BUILD_TESTING         OFF CACHE BOOL "Disable CTest suite"               FORCE)
set(PROJECTM_BUILD_TESTS  OFF CACHE BOOL "Disable projectM unit tests"        FORCE)
set(PROJECTM_BUILD_SDLUI  OFF CACHE BOOL "Disable SDL2 standalone frontend"   FORCE)
set(ENABLE_LLVM           OFF CACHE BOOL "Disable LLVM JIT expression engine" FORCE)

# Build projectM as a static library so it is linked directly into our JNI .so
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "Static build for JNI bundling"      FORCE)

# Use the GLM copy that projectM manages itself
set(PROJECTM_USE_SYSTEM_GLM OFF CACHE BOOL "Use projectM-bundled GLM" FORCE)

# ---------------------------------------------------------------------------
# Fetch projectM from the official repository at a pinned release tag.
# GIT_SHALLOW is set to FALSE so that any git submodules inside the
# projectM tree are also checked out correctly.
# ---------------------------------------------------------------------------
FetchContent_Declare(projectm
    GIT_REPOSITORY     https://github.com/projectM-visualizer/projectm.git
    GIT_TAG            v4.1.6
    GIT_SHALLOW        FALSE
    GIT_SUBMODULES_RECURSE TRUE
)

FetchContent_MakeAvailable(projectm)

# ---------------------------------------------------------------------------
# REPRODUCIBILITY BLOCK
# ---------------------------------------------------------------------------

# Strip paths for both YOUR source AND FetchContent's downloaded source
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")

# Strip Android NDK paths
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${ANDROID_NDK}=./ndk")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${ANDROID_NDK}=./ndk")

# Block timestamps
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Werror=date-time")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=date-time")

# Force deterministic static archives (.a files) for TagLib
# The 'D' flag tells ar to use zero for UIDs, GIDs, and timestamps.
set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")

# ---------------------------------------------------------------------------
# Felicity milkdrop JNI bridge
# ---------------------------------------------------------------------------
add_library(felicity_milkdrop SHARED
    milkdrop-bridge.cpp)

set_target_properties(felicity_milkdrop PROPERTIES
    CXX_STANDARD          17
    CXX_STANDARD_REQUIRED ON)

# Propagated include dirs from the projectM target cover the public headers.
# The explicit path below ensures the C API header is reachable even if
# projectM does not propagate its includes (older CMake versions).
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

# Force the debug postfix to be completely empty
set(CMAKE_DEBUG_POSTFIX "")
