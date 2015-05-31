#ifndef CSC2015THREADPOOL_TESTS_H
#define CSC2015THREADPOOL_TESTS_H

#include <iostream>

#define ensure(x) if (!(x)) { std::cout << "Ensure failed: " << #x << " at " << __FILE__ << ":" << __LINE__ << std::endl; return false; }

#endif //CSC2015THREADPOOL_TESTS_H
