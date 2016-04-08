#include <iostream>

#include <unistd.h>

#include "error.h"

void error(const char *msg)
{
	std::cout << "ERROR: " << msg << std::endl;
	exit(1);
}
