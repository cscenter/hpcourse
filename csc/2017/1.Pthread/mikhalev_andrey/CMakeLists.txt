cmake_minimum_required(VERSION 2.8)
project(pthread_producer_consumer)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -pthread")

set(SOURCE_FILES pthread_consumer_producer.cpp)
add_executable(lab ${SOURCE_FILES})