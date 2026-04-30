cmake_minimum_required(VERSION 3.22.1)
project("felicity_audio_engine")

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

# Force the linker to strip the final .so (fixing the 19MB vs 2MB issue)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-s")

# Include the FetchContent module
include(FetchContent)

# Declare where to get PFFFT.
# Using a specific commit hash ensures build NEVER breaks unexpectedly.
FetchContent_Declare(pffft
    GIT_REPOSITORY https://github.com/marton78/pffft.git
    GIT_TAG 0b4ee12c4ba45a4a8e567550c16d96d1679f50ce
)

# Download and make the library available to CMake
FetchContent_MakeAvailable(pffft)

# JNI bridge code
add_library(felicity_audio_engine SHARED
            visualizer-fft.cpp
            dsp-engine.cpp
            aaudio-player.cpp)

# Explicitly add pffft's source root so that #include "pffft.h" resolves
# correctly regardless of how the PFFFT::PFFFT target exposes its headers.
target_include_directories(felicity_audio_engine PRIVATE ${pffft_SOURCE_DIR})

# Link it all together
find_library(log-lib log)
find_library(aaudio-lib aaudio)

# we use the namespace PFFFT::PFFFT provided by their CMakeLists
target_link_libraries(felicity_audio_engine
                      PFFFT::PFFFT
                      ${log-lib}
                      ${aaudio-lib}
                      m)

