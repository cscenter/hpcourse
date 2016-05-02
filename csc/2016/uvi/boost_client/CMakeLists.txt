cmake_minimum_required(VERSION 3.3)

set(PROJECT_NAME boost_client)
project(${PROJECT_NAME})

find_package(Threads)
find_package(Boost 1.54.0 COMPONENTS system filesystem REQUIRED)
find_package(Protobuf REQUIRED)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(SOURCE_FILES main.cpp client.cpp client.hpp
        ../boost_server/protocol.pb.h ../boost_server/protocol.pb.cc
        ../boost_server/data_handler.cpp ../boost_server/data_handler.hpp)

add_executable(${PROJECT_NAME} ${SOURCE_FILES})
target_link_libraries(${PROJECT_NAME} Threads::Threads)
target_link_libraries (${PROJECT_NAME} ${Boost_LIBRARIES})
target_link_libraries (${PROJECT_NAME} ${PROTOBUF_LIBRARIES})
