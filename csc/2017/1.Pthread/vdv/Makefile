all: main.o posix_threads.o
	g++ -pthread posix_threads.o main.o
main.o: main.cpp
	g++ -Wall -pthread -std=c++11 -c main.cpp
posix_threads.o: posix_threads.cpp
	g++ -Wall -pthread -std=c++11 -c posix_threads.cpp
clean:
	rm *.o
