cmake_minimum_required(VERSION 3.1)
project(hps)

if(CMAKE_COMPILER_IS_GNUCXX)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -Wpedantic")
endif()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(THREADS_PREFER_PTHREAD_FLAG ON)

find_package(Threads REQUIRED)
find_package(Protobuf REQUIRED)

set(SOURCES dispatcher.cpp error.cpp protocol.pb.cc server.cpp socket_init.cpp socket_rw.cpp worker.cpp)
add_executable(hps ${SOURCES})

target_link_libraries(hps Threads::Threads)
target_link_libraries(hps ${PROTOBUF_LIBRARIES})

install(TARGETS hps RUNTIME DESTINATION bin)