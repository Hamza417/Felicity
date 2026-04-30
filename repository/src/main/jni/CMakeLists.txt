cmake_minimum_required(VERSION 3.22.1)
project("felicity_taglib")

set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# ---------------------------------------------------------------------------
# REPRODUCIBILITY BLOCK
# ---------------------------------------------------------------------------

# Strip paths for both YOUR source AND FetchContent's downloaded source
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffile-prefix-map=${CMAKE_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=./build")

# Block timestamps
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Werror=date-time")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=date-time")

# Force deterministic static archives (.a files) for TagLib
# The 'D' flag tells ar to use zero for UIDs, GIDs, and timestamps.
set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")

# Force the linker to strip the final .so (fixing the 19MB vs 2MB issue)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-s")

# ---------------------------------------------------------------------------

include(FetchContent)

set(CMAKE_DEBUG_POSTFIX "")

FetchContent_Declare(
        taglib
        GIT_REPOSITORY https://github.com/taglib/taglib.git
        GIT_TAG v2.2.1
        GIT_SHALLOW TRUE
        GIT_SUBMODULES_RECURSE TRUE
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(TAGLIB_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(TAGLIB_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WITH_ZLIB OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(taglib)

add_library(taglib_jni SHARED taglib_jni.cpp)
target_compile_features(taglib_jni PRIVATE cxx_std_17)

# ---------------------------------------------------------------------------
# The "Flatten" Hack for TagLib headers
# ---------------------------------------------------------------------------
set(TAGLIB_FLAT_DIR ${CMAKE_CURRENT_BINARY_DIR}/taglib_flat_includes/taglib)
file(MAKE_DIRECTORY ${TAGLIB_FLAT_DIR})

file(GLOB_RECURSE TAGLIB_HEADERS
        "${taglib_SOURCE_DIR}/taglib/*.h"
        "${taglib_SOURCE_DIR}/taglib/*.tcc"
)

# Sort the glob to guarantee deterministic ordering
list(SORT TAGLIB_HEADERS)

file(COPY ${TAGLIB_HEADERS} DESTINATION ${TAGLIB_FLAT_DIR})

target_include_directories(taglib_jni PRIVATE
        ${CMAKE_CURRENT_BINARY_DIR}/taglib_flat_includes
        ${taglib_BINARY_DIR}
)

target_link_libraries(taglib_jni
        PRIVATE
        tag
        log
)