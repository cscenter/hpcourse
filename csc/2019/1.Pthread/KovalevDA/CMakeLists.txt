cmake_minimum_required(VERSION 3.13)
project(KovalevDA)

set(CMAKE_CXX_STANDARD 11)

find_package (Threads)
add_executable(lab main.cpp)
target_link_libraries (lab ${CMAKE_THREAD_LIBS_INIT})