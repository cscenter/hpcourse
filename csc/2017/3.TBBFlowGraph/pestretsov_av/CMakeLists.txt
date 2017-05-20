cmake_minimum_required(VERSION 3.7)
project(tbb_lab)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -ltbb")

set(SOURCE_FILES flow.cpp)
add_executable(tbb_lab ${SOURCE_FILES})
