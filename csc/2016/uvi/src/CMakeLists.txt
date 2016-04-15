cmake_minimum_required(VERSION 3.3)

set(PROJECT_NAME Parallel)
project(${PROJECT_NAME})

find_package (Threads)
find_package(Protobuf REQUIRED)
find_package(Boost 1.54.0 COMPONENTS system filesystem REQUIRED)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(SOURCE_FILES main.cpp protocol.pb.cc protocol.pb.h tcp_server.cpp tcp_client.cpp server.cpp server.hpp task.cpp task.hpp task_manager.cpp task_manager.hpp client.cpp client.hpp)

add_executable(${PROJECT_NAME} ${SOURCE_FILES})
target_link_libraries (${PROJECT_NAME} Threads::Threads)
target_link_libraries (${PROJECT_NAME} ${PROTOBUF_LIBRARIES})
target_link_libraries (${PROJECT_NAME} ${Boost_LIBRARIES})




#set(Boost_USE_STATIC_LIBS OFF)
#set(Boost_USE_MULTITHREADED ON)


#include_directories(/usr/local/Cellar/boost/1.60.0_1/include)

