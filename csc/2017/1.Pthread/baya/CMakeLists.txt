cmake_minimum_required(VERSION 3.6)
project(parallel)

set(CMAKE_CXX_STANDARD 11)

set(SOURCE_FILES main.cpp)
add_executable(parallel ${SOURCE_FILES})
target_link_libraries(parallel pthread)