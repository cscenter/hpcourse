CPP_FLAGS		   ?=-c -Wall -O2
COMPILE_FLAGS 	   ?=-std=c++11 -lprotobuf -lboost_system -g
CLIENT_FLAGS       ?=-lboost_thread -lboost_atomic

all: Server Client

Server: server_main.o Server.o SocketListenerBase.o ServerTask.o protocol.pb.o
	g++ server_main.o Server.o SocketListenerBase.o ServerTask.o protocol.pb.o -o Server $(COMPILE_FLAGS) $(CLIENT_FLAGS)

Client: client_main.o Client.o SocketListenerBase.o protocol.pb.o
	g++ client_main.o Client.o SocketListenerBase.o protocol.pb.o -o Client $(COMPILE_FLAGS) $(CLIENT_FLAGS)

HEADERS = $(wildcard *.h*)

%.o: %.cpp ${HEADERS}
	g++ -c $< $(COMPILE_FLAGS) $(CLIENT_FLAGS)

clean:
	rm -rf *.o
