cmake_minimum_required(VERSION 3.22.1)
project(cedar_native LANGUAGES CXX)

add_library(cedar_native SHARED native_bridge.cpp)
target_compile_features(cedar_native PRIVATE cxx_std_20)
target_link_libraries(cedar_native android log)
