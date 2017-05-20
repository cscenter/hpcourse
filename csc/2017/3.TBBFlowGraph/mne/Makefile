CC=g++
CFLAGS=-c -Wall
LDFLAGS=-ltbb
EXECUTABLE=flowgraph
DIR=target

all: $(DIR) $(DIR)/flowgraph 
    
$(DIR)/flowgraph: $(DIR)/flowgraph.o
	$(CC) $(LDFLAGS) $< -o $@

$(DIR)/%.o: %.cpp
	$(CC) $(CFLAGS) $< -o $@

$(DIR):
	mkdir -p $(DIR)

clean:
	rm -rf target

