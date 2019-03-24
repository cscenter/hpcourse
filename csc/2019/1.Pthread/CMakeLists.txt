cmake_minimum_required(VERSION 3.0)
project(pthread-mlogachev)

set(CMAKE_CXX_STANDARD 11)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror")

add_executable(thing_1 template.cpp)