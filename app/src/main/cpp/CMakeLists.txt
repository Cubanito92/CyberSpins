cmake_minimum_required(VERSION 3.22.1)

project("radioengine")

# Find Oboe package via Prefab (provided by com.google.oboe:oboe dependency in Gradle)
find_package(oboe REQUIRED CONFIG)

add_library(radioengine SHARED
    native-lib.cpp
    AudioEngine.cpp
    IcecastStreamer.cpp
)

target_link_libraries(radioengine
    oboe::oboe
    log
    OpenSLES
)
