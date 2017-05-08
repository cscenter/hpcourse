cmake_minimum_required(VERSION 3.3)
project(PosixHW_1)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

set(SOURCE_FILES main.cpp)
add_executable(PosixHW_1 ${SOURCE_FILES})