
lab: *.cpp Makefile
	g++ -std=c++11 -pthread *.cpp -o $@

safe_lab: *.cpp Makefile
	g++ -std=c++11 -pthread -fsanitize=address  -fno-omit-frame-pointer *.cpp -o $@ 

