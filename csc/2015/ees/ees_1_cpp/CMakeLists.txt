cmake_minimum_required(VERSION 2.8)
project(concurrent_programming_thread_pool)

find_package(Boost COMPONENTS system program_options thread REQUIRED)
find_package(GTest)

include_directories(${GTEST_INCLUDE_DIRS})
enable_testing()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++1y -pthread")
set(SOURCE_FILES thread_pool.cpp log.cpp state.cpp)

add_library(thread_pool STATIC ${SOURCE_FILES})
add_executable(${CMAKE_PROJECT_NAME} app.cpp)
add_executable(${CMAKE_PROJECT_NAME}_test test.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}_test -lpthread thread_pool ${Boost_LIBRARIES} /usr/lib/libgtest.a /usr/lib/libgtest_main.a)
target_link_libraries(${CMAKE_PROJECT_NAME} -lpthread thread_pool ${Boost_LIBRARIES})
