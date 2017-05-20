cmake_minimum_required(VERSION 3.0)
project(Parallels3)

list(APPEND CMAKE_MODULE_PATH "<~/CLionProjects/FindTBB>")
set(SOURCE_FILES main.cpp)

find_package(TBB COMPONENTS tbbmalloc tbbmalloc_proxy tbb_preview)

add_executable(Parallels3 ${SOURCE_FILES})
target_link_libraries(Parallels3 tbb)

