#include <stdlib.h>
#include "simple.h"

int64_t task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n)
{
  while (n-- > 0) 
  {
    b = (a * p + b) % m;
    a = b;
  }
  return a;
}
