#include <pthread.h>


#define NOERROR 0
#define OVERFLOW 1


int get_last_error() {
  // return per-thread error code
}


void set_last_error(int code) {
  // set per-thread error code
}


void* producer_routine(void* arg) {
  // wait for consumer to start
 
  // read data, loop through each value and update the value, notify consumer, wait for consumer to process
}
 
void* consumer_routine(void* arg) {
  // notify about start
  // for every update issued by producer, read the value and add to sum
  // return pointer to result (for particular consumer)
}
 
void* consumer_interruptor_routine(void* arg) {
  // wait for consumers to start
 
  // interrupt random consumer while producer is running                                          
}
 
int run_threads() {
  int sum = 0;

  // start N threads and wait until they're done
  // return aggregated sum of values

  std::cout << sum << std::endl;
  return 0;
}
 
int main() {
  return run_threads();
}
