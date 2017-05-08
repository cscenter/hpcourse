cmake_minimum_required(VERSION 2.6)
project(Pthread_pag)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread ")

add_executable(Pthread_pag main.cpp)