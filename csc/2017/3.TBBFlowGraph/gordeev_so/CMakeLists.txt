cmake_minimum_required(VERSION 3.6)
project(flow)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}  -std=c++14 -Wall ")
set(PROJECT_LINK_LIBS -ltbb)

set(SOURCE_FILES flow.cpp)

add_executable(flow ${SOURCE_FILES})
target_link_libraries(flow ${PROJECT_LINK_LIBS})