cmake_minimum_required(VERSION 3.22.1)
project("felicity_audio_engine")

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

