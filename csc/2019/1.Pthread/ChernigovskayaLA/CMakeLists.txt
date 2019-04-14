cmake_minimum_required(VERSION 3.13)
project(ChernigovskayaLA)

set(CMAKE_CXX_STANDARD 11)

add_executable(lab main.cpp)

set(THREADS_PREFER_PTHREAD_FLAG ON)
find_package(Threads REQUIRED)
target_link_libraries(lab Threads::Threads)