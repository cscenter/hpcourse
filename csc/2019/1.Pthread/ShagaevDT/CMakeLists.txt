cmake_minimum_required(VERSION 2.8)
project(ShagaevDT)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

set(SOURCE_FILES template.cpp)
add_executable(lab ${SOURCE_FILES})