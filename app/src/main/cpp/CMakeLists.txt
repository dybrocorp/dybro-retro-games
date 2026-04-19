cmake_minimum_required(VERSION 3.22.1)

project("retro")

add_library(
    retro
    SHARED
    native-lib.cpp
    CoreManager.cpp
    VideoRenderer.cpp
    AudioRenderer.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    retro
    ${log-lib}
    android
    aaudio
)
