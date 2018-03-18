cmake_minimum_required(VERSION 3.5)
project(TankovVD)

set(CMAKE_CXX_STANDARD 11)

add_executable(TankovVD Template.cpp)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)
target_link_libraries(TankovVD Threads::Threads)
