#include <pthread.h>
#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <stdlib.h>
#include <chrono>
#include <thread>

using namespace std;

#define NOERROR 0
#define OVERFLOW 1

pthread_key_t errorKey;

struct sync_data {
  pthread_mutex_t lock;
  pthread_cond_t dataChanged, dataProcessed;
  int curValue;
  bool shouldContinue, dataExists; // no Optional in c++11, so use separate flag
  pthread_barrier_t consumersHold;
};

struct consumer_data {
  sync_data* sync;
  int partialSum;
  int errorCode;
  int maxSleep;
};

struct interruptor_data {
  sync_data* sync;
  vector<pthread_t>* consumers;
};

// int get_last_error() {
//   auto val = pthread_getspecific(errorKey);
//   return (int)val;
// }

// void set_last_error(int code) {
//   // set per-thread error code
// }

void log(string msg){
  // cout << msg << endl;
  //this_thread::sleep_for(chrono::seconds(3));
}


void* producer_routine(void* arg) {
  // wait for consumer to start
  
  // read data, loop through each value and update the value, notify consumer, wait for consumer to process
  sync_data* sync = (sync_data*)arg;
  //cout << &sync << endl;

  string rawInput;
  getline(cin, rawInput);
  stringstream stream(rawInput);
  int cur;

  while(true) {
    stream >> cur;
    if(!stream){
      break;
    }
    
    pthread_mutex_lock(&sync->lock);
    log("producer locked");

    sync->curValue=cur;
    sync->dataExists=true;
    pthread_cond_signal(&sync->dataChanged);
    log( "producer signaled");

    // wait for some thread to read new data
    while (sync->dataExists) {
      log("producer waits");
      pthread_cond_wait(&sync->dataProcessed, &sync->lock);
      log("producer catched signal");
    }
    log( "producer ensured");
    // now can safely proceed
    pthread_mutex_unlock(&sync->lock);
    log("producer unlocked");
  }

  // now stop consumers
  pthread_mutex_lock(&sync->lock);
  // at this moment every consumer is either outside lock-unlock block, or is locked on lock(), or is waiting on wait()
  sync->shouldContinue=false;
  pthread_cond_broadcast(&sync->dataChanged); // to wake up consumers of third case
  // consumers of first case will check the outer condition, and those of second or third will check the condition above wait()
  pthread_mutex_unlock(&sync->lock);
  
}
 
void* consumer_routine(void* arg) {
  consumer_data* data = (consumer_data*)arg;
  sync_data* sync = data->sync;

  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
  pthread_barrier_wait(&sync->consumersHold);
  
  int value, localSum=0;
  
  while (true) {
    pthread_mutex_lock(&sync->lock);
    log("consumer locked");

    while (!sync->dataExists && sync->shouldContinue){
      log("consumer waits");
      pthread_cond_wait(&sync->dataChanged, &sync->lock);
      log("consumer catched signal");
    }    
    if (sync->shouldContinue){
      value=sync->curValue;
      sync->dataExists=false; // prevent other consumers from reading this value
      pthread_cond_signal(&sync->dataProcessed);
      log("consumer signaled");
      localSum+=value;
      pthread_mutex_unlock(&sync->lock);
    } else {
      pthread_mutex_unlock(&sync->lock);
      break;
    }
    log("consumer unlocked");

    // supports at least 32 seconds pause, see http://www.cplusplus.com/reference/cstdlib/rand/
    if (data->maxSleep > 0){
      int stopFor = rand() % data->maxSleep;
      // safe since no locks are held
      this_thread::sleep_for(chrono::milliseconds(stopFor));
    }
  }

  data->partialSum=localSum;
}
 
void* consumer_interruptor_routine(void* arg) {
  interruptor_data* data = (interruptor_data*)arg;
  pthread_barrier_wait(&data->sync->consumersHold);

  while (true){
    int targetNum = rand() % data->consumers->size();
    pthread_t target = (*(data->consumers))[targetNum];
    int result = pthread_cancel(target);
    // cout << result << endl;
    if (!data->sync->shouldContinue){
      break;
    }
  }
}
 
int run_threads(int consumersAmount, int maxSleep) {
  int totalSum = 0;
  
  vector<pthread_t> consumers(consumersAmount);
  vector<consumer_data> consumersData(consumersAmount);
  pthread_t producer;
  pthread_t interruptor;

  //pthread_key_create(&errorKey, NULL);
  sync_data sync;
  pthread_mutex_init(&sync.lock, NULL);
  pthread_cond_init(&sync.dataChanged, NULL);
  pthread_cond_init(&sync.dataProcessed, NULL);
  pthread_barrier_init(&sync.consumersHold, NULL, consumersAmount+1);
  sync.shouldContinue = true;
  sync.dataExists = false;
  interruptor_data irr_data = {sync: &sync, consumers: &consumers};
    
  pthread_create(&producer, NULL, producer_routine, &sync);
  for (int i=0; i<consumersAmount; i++){
    consumersData[i] = {sync: &sync, partialSum: 0, errorCode: 0, maxSleep: maxSleep};
    pthread_create(&consumers[i], NULL, consumer_routine, &consumersData[i]);
  }
  pthread_create(&interruptor, NULL, consumer_interruptor_routine, &irr_data);
  
  pthread_join(producer, NULL);
  pthread_join(interruptor, NULL);
  for (int i=0; i<consumersAmount; i++){
    pthread_join(consumers[i], NULL);
    totalSum+=consumersData[i].partialSum;
  }
  
  
  cout << totalSum << endl;

  pthread_cond_destroy(&sync.dataChanged);
  pthread_cond_destroy(&sync.dataProcessed);
  pthread_mutex_destroy(&sync.lock);
  pthread_barrier_destroy(&sync.consumersHold);
  //pthread_key_destroy(&errorKey);
  return 0;
}
 
int main(int argc, char *argv[]) {
  return run_threads(atoi(argv[1]), atoi(argv[2]));
}
