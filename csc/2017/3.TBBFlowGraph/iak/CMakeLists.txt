cmake_minimum_required(VERSION 3.7)
project(iak)

set(CMAKE_CXX_STANDARD 11)

set(SOURCE_FILES
        image_pattern_search.cpp)
link_libraries(tbb)
add_executable(iak ${SOURCE_FILES})