cmake_minimum_required(VERSION 3.3)
project(task1)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set (Boost_USE_MULTITHREADED ON)
set (Boost_USE_STATIC_LIBS ON)

find_package( Boost REQUIRED COMPONENTS thread system)
include_directories( ${Boost_INCLUDE_DIR} )

set(SOURCE_FILES main.cpp protocol.pb.cc)
add_executable(task1 ${SOURCE_FILES})

find_package(Protobuf REQUIRED)
include_directories(${PROTOBUF_INCLUDE_DIRS})

target_link_libraries( task1 ${Boost_LIBRARIES} ${PROTOBUF_LIBRARIES})
