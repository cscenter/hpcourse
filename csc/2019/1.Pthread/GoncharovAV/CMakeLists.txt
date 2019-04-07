cmake_minimum_required(VERSION 3.12)
project(GoncharovAV)

set(CMAKE_CXX_STANDARD 14)

add_executable(lab1 main.cpp)

TARGET_LINK_LIBRARIES(lab1 pthread)