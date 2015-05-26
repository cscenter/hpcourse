set(APP_NAME THREAD_POOL)

include_directories(command_line_parser thread_pool)

set(COMMAND_LINE_PARSER_FILES
    command_line_parser/command_line_parser.cpp
    command_line_parser/command_line_parser.hpp
    )

set(THREAD_POOL_FILES
    thread_pool/thread_pool.cpp
    thread_pool/thread_pool.hpp
    )

set(ALL_FILES
    ${COMMAND_LINE_PARSER_FILES}
    ${THREAD_POOL_FILES}
    main.cpp
    )

############################################################################################

add_executable(${APP_NAME} ${ALL_FILES})
target_link_libraries(${APP_NAME} ${Boost_LIBRARIES})
set_target_properties(${APP_NAME} PROPERTIES OUTPUT_NAME_DEBUG "${APP_NAME}d")
set_target_properties(${APP_NAME} PROPERTIES OUTPUT_NAME_RELEASE ${APP_NAME})

############################################################################################

# Project settings
if (MSVC)

    # disable: warning C4512: '***' : assignment operator could not be generated
    add_definitions(/wd4512)

    source_group("Source Files\\COMMAND LINE PARSER" FILES ${COMMAND_LINE_PARSER_FILES})
    source_group("Source Files\\THREAD POOL" FILES ${THREAD_POOL_FILES})
    source_group("Source Files" FILES main.cpp)

    set(CMAKE_CXX_FLAGS                "/EHsc /W4 /nologo" CACHE STRING "" FORCE)
    set(CMAKE_CXX_FLAGS_DEBUG          "/D_DEBUG /MDd /Zi /Ob0 /Od /RTC1")
    set(CMAKE_CXX_FLAGS_MINSIZEREL     "/MD /O1 /Ob1 /D NDEBUG")
    set(CMAKE_CXX_FLAGS_RELEASE        "/MD /O2 /Ob2 /D NDEBUG")
    set(CMAKE_CXX_FLAGS_RELWITHDEBINFO "/MD /Zi /O2 /Ob1 /D NDEBUG")

    set_target_properties(${APP_NAME} PROPERTIES LINK_FLAGS "/DYNAMICBASE:NO /FIXED")

elseif (CMAKE_COMPILER_IS_GNUCXX)

    # Setting directory
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIRECTORY} CACHE STRING "" FORCE)

    # Compiler flags
    set(CMAKE_CXX_FLAGS "-Wall -std=c++11" CACHE STRING "" FORCE)
    set(CMAKE_CXX_FLAGS_DEBUG "-g" CACHE STRING "" FORCE)
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE}" CACHE STRING "" FORCE)

    # Linker flags
    set(CMAKE_EXE_LINKER_FLAGS "-static-libgcc -static-libstdc++ -static" CACHE STRING "" FORCE)
    set(CMAKE_SHARED_LIBRARY_LINK_CXX_FLAGS "" CACHE STRING "" FORCE)
    set(BUILD_SHARED_LIBS OFF)

endif (MSVC)

############################################################################################
