#!/usr/bin/python
# -*- coding: UTF-8 -*-

import os
import platform
import logging

def solution(directory_solution, cmake_generator, cmake_build_type, build_project):
    """
    """
    # logging.basicConfig(level=logging.DEBUG)

    source_directory = os.getcwd()
    generate_project = r'cmake -G ' + cmake_generator + ' '
    if cmake_generator[1:13] != 'Visual Studio':
        generate_project += '-DCMAKE_BUILD_TYPE=' + cmake_build_type + ' '
    generate_project += source_directory

    logging.debug(generate_project)
    logging.debug(build_project)

    if not os.path.isdir(directory_solution):
        os.makedirs(directory_solution)

    os.chdir(directory_solution)
    os.system(generate_project)
    os.system(build_project)


if __name__ == "__main__":
    # name_dir = os.getcwd()
    # name_dir = name_dir.split("\\")
    # directory_solution = r'../../Build/' + name_dir[-1]
    directory_solution = r'build/'

    cmake_generator = ['"MinGW Makefiles"', '"Visual Studio 12"']
    cmake_build_type = ['Debug', 'Release']
    build_project = ['mingw32-make', '']

    build_flag = 1
    name_system = platform.system()
    if name_system == 'Windows':
        compiler_flag = 1
        solution(directory_solution, cmake_generator[compiler_flag], cmake_build_type[build_flag],
                 build_project[compiler_flag])
    elif name_system == 'Linux' or name_system == 'Darwin':
        solution(directory_solution, '"Unix Makefiles"', cmake_build_type[build_flag], 'make')
