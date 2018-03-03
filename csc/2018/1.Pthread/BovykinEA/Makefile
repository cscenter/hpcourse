build:
	g++ -std=c++11 -pthread main.cpp -o main
run: build
	cat in.txt | ./main
	@echo "^ this should equal 1000"

test: build
	cat in2.txt | ./main
	@echo "^ this should equal 6"
