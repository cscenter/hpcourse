CPP_COMPILER=g++
COMPILER_FLAGS=-pthread

all: lab

clean:
	rm lab

lab: Template.cpp
	${CPP_COMPILER} -std=c++11 ${COMPILER_FLAGS} $< -o $@  

gdblab: Template.cpp
	${CPP_COMPILER} -g -std=c++11 ${COMPILER_FLAGS} $< -o $@  
