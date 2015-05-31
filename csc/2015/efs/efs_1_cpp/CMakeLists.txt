cmake_minimum_required(VERSION 3.2)
project(Csc2015ThreadPool)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Werror -Wshadow -O2")
IF (MINGW)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,--stack=33554432")
ENDIF()

set(SOURCE_FILES ThreadPool.cpp ThreadPool.h)
add_executable(threadpool_console ${SOURCE_FILES} console.cpp)
add_executable(threadpool_tests ${SOURCE_FILES} tests/main.cpp tests/mergesort.h tests/tests.h tests/load_distribution.h tests/dfs.h tests/interruption.h)

set_property(TARGET threadpool_console PROPERTY CXX_STANDARD 11)
set_property(TARGET threadpool_tests PROPERTY CXX_STANDARD 11)
