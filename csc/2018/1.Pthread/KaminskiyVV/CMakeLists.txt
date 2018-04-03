cmake_minimum_required(VERSION 3.9)
project(KaminskiyVV)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

add_executable(KaminskiyVV main.cpp)