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

# Block timestamps — applied only to our own sources, not to FetchContent dependencies.
# We use target_compile_options on felicity_audio_engine below instead of a global flag
# so that Oboe's own sources (which we don't own) are not affected.
# set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Werror=date-time")
# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Werror=date-time")

# Force deterministic static archives (.a files) for TagLib
# The 'D' flag tells ar to use zero for UIDs, GIDs, and timestamps.
set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> qcDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> qDT <TARGET> <LINK_FLAGS> <OBJECTS>")

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

# ---------------------------------------------------------------------------
# libusb — pulled at build time so we always get a known-good version.
# v1.0.27 is the first release that ships a first-class CMakeLists.txt, which
# means FetchContent_MakeAvailable works without any manual source wiring.
# ---------------------------------------------------------------------------
FetchContent_Declare(
        libusb
        GIT_REPOSITORY https://github.com/libusb/libusb-cmake.git
        GIT_TAG v1.0.30-0
)
FetchContent_MakeAvailable(libusb)

# ---------------------------------------------------------------------------
# Oboe — Google's C++ audio library that wraps AAudio (API 26+) and falls
# back to OpenSL ES on older devices. Pinned to a stable release tag so the
# build is reproducible.
# ---------------------------------------------------------------------------
FetchContent_Declare(
        oboe
        GIT_REPOSITORY https://github.com/google/oboe.git
        GIT_TAG 1.10.0
)
FetchContent_MakeAvailable(oboe)

# The USB DAC driver is a separate shared library so it can be loaded
# independently of the main audio engine. The Kotlin side loads it via
# System.loadLibrary("felicity_usb_dac") inside UsbDacDriver.
add_library(felicity_usb_dac SHARED
        felicity_usb_dac.cpp
        uac_parser.cpp
        uac_negotiator.cpp
        usb_iso_stream.cpp)

find_library(log-lib-usb log)

target_include_directories(felicity_usb_dac PRIVATE
        ${libusb_SOURCE_DIR}/libusb)

target_link_libraries(felicity_usb_dac
        usb-1.0          # target name exposed by libusb's own CMakeLists.txt
        ${log-lib-usb})

# JNI bridge code
add_library(felicity_audio_engine SHARED
            visualizer-fft.cpp
            dsp-engine.cpp
        aaudio-player.cpp
        oboe-player.cpp)

# Explicitly add pffft's source root so that #include "pffft.h" resolves
# correctly regardless of how the PFFFT::PFFFT target exposes its headers.
target_include_directories(felicity_audio_engine PRIVATE ${pffft_SOURCE_DIR})

# Enforce no __DATE__ / __TIME__ macros in our own source only.
# Applying this globally would break Oboe's internal files that we don't control.
target_compile_options(felicity_audio_engine PRIVATE -Werror=date-time)

# Link it all together
find_library(log-lib log)
find_library(aaudio-lib aaudio)
find_library(opensl-lib OpenSLES)

# we use the namespace PFFFT::PFFFT provided by their CMakeLists
target_link_libraries(felicity_audio_engine
                      PFFFT::PFFFT
        oboe
                      ${log-lib}
                      ${aaudio-lib}
        ${opensl-lib}
                      m)

