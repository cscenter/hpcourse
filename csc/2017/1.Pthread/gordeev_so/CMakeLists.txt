cmake_minimum_required(VERSION 2.6)
project(pthreads_hw)

SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

add_executable(pthreads_hw main.cpp)

install(TARGETS pthreads_hw RUNTIME DESTINATION bin)
