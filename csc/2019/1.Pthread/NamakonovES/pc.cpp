#include <pthread.h>
#include <iostream>
#include <vector>
#include <string>
#include <sstream>
#include <stdlib.h>
#include <chrono>
#include <thread>
#include <limits>

using namespace std;

#define NOERROR 0
#define OVERFLOW 1
int noerrorValue=NOERROR, overflowValue=OVERFLOW;
int *noerrorPtr=&noerrorValue, *overflowPtr=&overflowValue;

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

int get_last_error() {
  void* ptr = pthread_getspecific(errorKey);
  if (ptr == overflowPtr){
    return OVERFLOW;
  } else if (ptr == noerrorPtr){
    return NOERROR;
  } else {
    cout << "UNKNOWN CODE GET";
    return -1;
  }
}

void set_last_error(int code) {
  int* target;
  if (code == OVERFLOW){
    target = overflowPtr;
  } else if (code == NOERROR){
    target = noerrorPtr;
  } else {
    cout << "UNKNOWN CODE SET";
    return;
  }
  pthread_setspecific(errorKey, (void*)target);
}

void log(string msg){
  // cout << msg << endl;
  //this_thread::sleep_for(chrono::seconds(3));
}


void* producer_routine(void* arg) {
  // wait for consumer to start
  
  // read data, loop through each value and update the value, notify consumer, wait for consumer to process
  sync_data* sync = (sync_data*)arg;

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
  set_last_error(NOERROR);
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
      if (((value > 0) && (localSum+value<localSum)) ||
	  ((value < 0) && (localSum+value>localSum))) {
	// under/over-flow occured
	set_last_error(OVERFLOW);
	pthread_mutex_unlock(&sync->lock);
	break;
      } else {
	localSum+=value;
	pthread_mutex_unlock(&sync->lock);	
      }
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

  data->errorCode=get_last_error();
  data->partialSum=localSum;  
}
 
void* consumer_interruptor_routine(void* arg) {
  interruptor_data* data = (interruptor_data*)arg;
  pthread_barrier_wait(&data->sync->consumersHold);

  // don't use synchronization since only way this thread can hang is when he not sees shouldContinue write
  // (is it possible?)
  // and this thread can't cause other threads to hang
  while (true){
    int targetNum = rand() % data->consumers->size();
    pthread_t target = (*(data->consumers))[targetNum];
    int result = pthread_cancel(target);
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

  pthread_key_create(&errorKey, NULL);
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
  // first join all consumers, then process results
  for (int i=0; i<consumersAmount; i++){
    pthread_join(consumers[i], NULL);
  }
  bool successful=true;
  for (int i=0; i<consumersAmount; i++){
    if (consumersData[i].errorCode==NOERROR){
      int partialSum=consumersData[i].partialSum;
      if (((partialSum > 0) && (totalSum+partialSum<totalSum)) ||
	((partialSum < 0) && (totalSum+partialSum>totalSum))) {
	successful=false;
	break;
      } else {
	totalSum+=partialSum;
      }      
    } else {
      successful=false;
      break;
    }    
  }
  
  pthread_cond_destroy(&sync.dataChanged);
  pthread_cond_destroy(&sync.dataProcessed);
  pthread_mutex_destroy(&sync.lock);
  pthread_barrier_destroy(&sync.consumersHold);
  pthread_key_delete(errorKey);

  if (successful){
    cout << totalSum << endl;
    return 0;
  } else {
    cout << "overflow" << endl;
    return 1;
  }
  
}
 
int main(int argc, char *argv[]) {
  //int imax = std::numeric_limits<int>::max();
  //cout << imax << endl;
  return run_threads(atoi(argv[1]), atoi(argv[2]));
}
