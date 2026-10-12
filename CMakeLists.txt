cmake_minimum_required(VERSION 3.22)
project(ultra_whisper LANGUAGES C CXX)

include(FetchContent)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.7.6
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(ultra_whisper SHARED whisper_jni.cpp)
target_link_libraries(ultra_whisper PRIVATE whisper android log)
