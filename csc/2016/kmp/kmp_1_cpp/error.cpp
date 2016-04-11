#include <iostream>

#include "error.h"

void error(const char *msg)
{
	std::cout << "ERROR: " << msg << std::endl;
	exit(1);
}
