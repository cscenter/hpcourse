cmake_minimum_required(VERSION 3.6)
project(hw1)

set(CMAKE_CXX_STANDARD 11)

FIND_PACKAGE(Threads)

set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3")

set(SOURCE_FILES main.cpp)
add_executable(posix_threads ${SOURCE_FILES})
target_link_libraries (posix_threads ${CMAKE_THREAD_LIBS_INIT})