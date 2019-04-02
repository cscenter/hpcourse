cmake_minimum_required(VERSION 3.13)
project(pthread)

set(CMAKE_CXX_STANDARD 11)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)

add_executable(pthread solution.cpp)

target_link_libraries(pthread Threads::Threads)
