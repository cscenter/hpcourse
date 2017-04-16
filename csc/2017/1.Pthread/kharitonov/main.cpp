#include <pthread.h>  
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

static pthread_cond_t  cond_consumer_ready = PTHREAD_COND_INITIALIZER;
static pthread_cond_t  cond_new_value      = PTHREAD_COND_INITIALIZER;

static pthread_mutex_t mutex_global = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t mutex_consumer_finished = PTHREAD_MUTEX_INITIALIZER;

static pthread_barrier_t barrier_start;

static bool new_value         = false;
static bool consumer_ready    = false;
static bool producer_finished = false;
static bool consumer_finished = false;

class Value {
public:
  Value() : _value(0) {}

  void update(int value) {
    _value = value;
  }

  int get() const {
    return _value;
  }
private:
  int _value;
};

void* producer_routine(void* arg) {
  Value &value = *(Value *)arg;

  // Wait for consumer to start
  pthread_barrier_wait(&barrier_start);

  // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
  std::vector<int> numbers;
  int val;

  while (std::cin >> val)
    numbers.push_back(val);

  pthread_mutex_lock(&mutex_global);

  for (int v: numbers) {
    while (!consumer_ready)
      pthread_cond_wait(&cond_consumer_ready, &mutex_global);
    consumer_ready = false;

    value.update(v);

    new_value = true;
    pthread_cond_signal(&cond_new_value);
  }

  producer_finished = true;

  pthread_mutex_unlock(&mutex_global);

  return NULL;
}

void* consumer_routine(void* arg) {
  Value &value = *(Value *)arg;

  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

  // notify about start
  pthread_barrier_wait(&barrier_start);

  // allocate value for result
  Value *res_value = new Value();

  // for every update issued by producer, read the value and add to sum
  pthread_mutex_lock(&mutex_global);

  while (!producer_finished) {
    consumer_ready = true;
    pthread_cond_signal(&cond_consumer_ready);

    while (!new_value)
      pthread_cond_wait(&cond_new_value, &mutex_global);
    new_value = false;

    res_value->update(res_value->get() + value.get());
  }
  pthread_mutex_unlock(&mutex_global);

  pthread_mutex_lock(&mutex_consumer_finished);
  consumer_finished = true;
  pthread_mutex_unlock(&mutex_consumer_finished);

  pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
  // return pointer to result
  return res_value;
}

void* consumer_interruptor_routine(void* arg) {
  pthread_t thread_consumer = *(pthread_t *)arg;
  // wait for consumer to start
  pthread_barrier_wait(&barrier_start);

  pthread_mutex_lock(&mutex_consumer_finished);
  bool consumer_finished_local = consumer_finished;
  pthread_mutex_unlock(&mutex_consumer_finished);

  while (!consumer_finished_local) {
    pthread_cancel(thread_consumer);

    pthread_mutex_lock(&mutex_consumer_finished);
    consumer_finished_local = consumer_finished;
    pthread_mutex_unlock(&mutex_consumer_finished);
  }

  // interrupt consumer while producer is running
  return NULL;
}

int run_threads() {
  // start 3 threads and wait until they're done
  Value common_value;
  pthread_t thProducer, thConsumer, thInterruptor;

  pthread_barrier_init(&barrier_start, NULL, 3);

  pthread_create(&thProducer,
                 NULL,
                 producer_routine,
                 &common_value);

  pthread_create(&thConsumer,
                 NULL,
                 consumer_routine,
                 &common_value);

  pthread_create(&thInterruptor,
                 NULL,
                 consumer_interruptor_routine,
                 &thConsumer);

  void *res_ptr;

  pthread_join(thProducer,    NULL);
  pthread_join(thInterruptor, NULL);
  pthread_join(thConsumer,    &res_ptr);

  int res_int = ((Value*)res_ptr)->get();
  delete (Value*)res_ptr;

  // return sum of update values seen by consumer
  return res_int;
}

int main() {
  std::cout << run_threads() << std::endl;
  return 0;
}
