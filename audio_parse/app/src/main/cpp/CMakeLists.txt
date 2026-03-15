cmake_minimum_required(VERSION 3.22.1)
project("whisper_jni")

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(WHISPER_SOURCES
    whisper.cpp
    ggml.c
    ggml-alloc.c
    ggml-backend.c
    ggml-quants.c
)

add_library(whisper_lib STATIC
    ${WHISPER_SOURCES}
)

target_include_directories(whisper_lib PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(whisper_lib PRIVATE
    GGML_USE_CPU
)

target_compile_options(whisper_lib PRIVATE
    -O3
    -DNDEBUG
)

add_library(whisper SHARED
    whisper_jni.cpp
)

target_include_directories(whisper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(whisper
    whisper_lib
    android
    log
)
