cmake_minimum_required(VERSION 3.9)
project(pthreads)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -pthread")
add_executable(pthreads hw1.cpp)