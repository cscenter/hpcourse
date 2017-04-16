cmake_minimum_required(VERSION 3.0.0 FATAL_ERROR)

# Project settings
set(PROJECT_NAME Parallel_programming)
project(${PROJECT_NAME})

set(VERSION_MAJOR 0)
set(VERSION_MINOR 1)
set(VERSION_PATCH 0)
set(VERSION ${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH})
message(STATUS "VERSION: " ${VERSION})

############################################################################################

# OS settings
if (WIN32 OR APPLE)
    set(CMAKE_INSTALL_PREFIX ${PROJECT_SOURCE_DIR}/build/install CACHE STRING "" FORCE)
    set(CMAKE_CONFIGURATION_TYPES "Debug;Release" CACHE STRING "" FORCE)
endif(WIN32 OR APPLE)

############################################################################################

# BOOST
set(Boost_USE_STATIC_LIBS        ON)
set(Boost_USE_MULTITHREADED      ON)
set(Boost_USE_STATIC_RUNTIME    OFF)
set(BOOST_COMPONENTS
    chrono
    date_time
    program_options
    system
    thread
    )

find_package(Boost 1.57.0 COMPONENTS ${BOOST_COMPONENTS} QUIET REQUIRED)
include_directories(${Boost_INCLUDE_DIRS})

############################################################################################

add_subdirectory(thread_pool)

############################################################################################
