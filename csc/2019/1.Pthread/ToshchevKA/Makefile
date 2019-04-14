# Build system for martices

CC = g++
CC_FLAGS = -g -Wall -Wextra -std=c++11 -pthread
BUILD_DIR   = ./
SOURCES_DIR = ./

all: pthread

pthread: $(BUILD_DIR)/main.o
	$(CC) $(CC_FLAGS) $(BUILD_DIR)/main.o -o lab

$(BUILD_DIR)/main.o: $(SOURCES_DIR)/main.cpp
	$(CC) $(CC_FLAGS) -c $(SOURCES_DIR)/main.cpp -o $(BUILD_DIR)/main.o

.PHONY: clean all
clean:
	rm -rf $(BUILD_DIR)/*.o
