cmake_minimum_required(VERSION 3.22.1)
project("felicity_taglib")

# Emit a compile_commands.json so clangd (the IDE's C++ language server)
# can find all the headers — including the ones FetchContent downloads.
# Without this, the IDE shows "file not found" for every TagLib include
# even though the actual Gradle build works perfectly fine.
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Pull TagLib directly from GitHub and build it as a static library.
# The first CMake configure step will download the source — make sure
# your build machine has internet access for that initial fetch.
include(FetchContent)

# Force the debug postfix to be completely empty
set(CMAKE_DEBUG_POSTFIX "")

FetchContent_Declare(
    taglib
    GIT_REPOSITORY https://github.com/taglib/taglib.git
    GIT_TAG        v2.2.1
    GIT_SHALLOW        TRUE
    GIT_SUBMODULES_RECURSE TRUE
)

# Turn off everything from TagLib's build we don't need.
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(TAGLIB_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(TAGLIB_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(WITH_ZLIB              OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(taglib)

# Our thin JNI shim that bridges Kotlin ↔ TagLib C++.
add_library(taglib_jni SHARED taglib_jni.cpp)

target_compile_features(taglib_jni PRIVATE cxx_std_17)

# ---------------------------------------------------------------------------
# The "Flatten" Hack for TagLib headers
# ---------------------------------------------------------------------------
# 1. Create a virtual 'taglib' directory inside the build cache
set(TAGLIB_FLAT_DIR ${CMAKE_CURRENT_BINARY_DIR}/taglib_flat_includes/taglib)
file(MAKE_DIRECTORY ${TAGLIB_FLAT_DIR})

# 2. Scrape every single header file from TagLib's scattered subdirectories
file(GLOB_RECURSE TAGLIB_HEADERS
    "${taglib_SOURCE_DIR}/taglib/*.h"
    "${taglib_SOURCE_DIR}/taglib/*.tcc"
)

# 3. Copy them all into our flat virtual directory so they sit side-by-side
file(COPY ${TAGLIB_HEADERS} DESTINATION ${TAGLIB_FLAT_DIR})

# 4. Point the JNI target to the parent of the flat directory,
#    and keep the binary dir for generated config headers.
target_include_directories(taglib_jni PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/taglib_flat_includes
    ${taglib_BINARY_DIR}
)

target_link_libraries(taglib_jni
    PRIVATE
    tag       # TagLib static library (built by FetchContent above)
    log       # Android logcat support
)

