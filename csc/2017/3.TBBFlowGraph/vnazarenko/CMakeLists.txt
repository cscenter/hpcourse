cmake_minimum_required(VERSION 3.5)
project(vnazarenko)

set(CMAKE_CXX_STANDARD 14)
SET( CMAKE_CXX_FLAGS  "${CMAKE_CXX_FLAGS} -O3" )

set(SOURCE_FILES flow.cpp)
add_executable(vnazarenko ${SOURCE_FILES})
target_link_libraries(vnazarenko -ltbb)