cmake_minimum_required(VERSION 3.6)
project(veg)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

set(SOURCE_FILES Task.cpp)
add_executable(veg ${SOURCE_FILES})
