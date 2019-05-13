cmake_minimum_required(VERSION 3.13)
project(parallel1)

SET(CMAKE_CXX_STANDARD 14)
SET(CMAKE_CXX_FLAGS -pthread)

add_executable(parallel2 main.cpp)