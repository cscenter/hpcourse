cmake_minimum_required(VERSION 3.1)

set(CMAKE_CXX_STANDARD 11)

project(lab)
add_executable(lab customer_interruptor.cpp)

target_link_libraries(lab pthread)