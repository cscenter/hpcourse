#include <pthread.h>  
#include <iostream>
#include <assert.h>
#include <stdio.h>
#include <stdbool.h>

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

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t can_produce =  PTHREAD_COND_INITIALIZER;
pthread_cond_t can_consume =  PTHREAD_COND_INITIALIZER;
pthread_t producer, consumer, interruptor;

bool can_produce_flag;
int eof;

void* producer_routine(void* arg) {
  pthread_mutex_lock(&mutex);

  Value *val = (Value *) arg;
  int new_int;
  int stat;

  while(true) {
    while (!can_produce_flag) {
      pthread_cond_wait(&can_produce, &mutex);
    }
    assert(can_produce_flag);

    //printf(">");
    stat = scanf("%d", &new_int);
    if (stat != EOF) {
      //printf("Producer: %d\n", new_int);
      val->update(new_int);
      can_produce_flag = false;
      pthread_cond_broadcast(&can_consume);
    } else {
      //printf("Producer: EOF\n");
      eof = 1;
      can_produce_flag = false;
      pthread_cond_broadcast(&can_consume);
      break;
    }
  }
  pthread_mutex_unlock(&mutex);
}

void* consumer_routine(void* arg) {
  pthread_mutex_lock(&mutex);

  Value *val = (Value *) arg;
  int *result = new int;

  pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

  can_produce_flag = true;
  pthread_cond_broadcast(&can_produce);

  while(1) {
    while (can_produce_flag) {
      pthread_cond_wait(&can_consume, &mutex);
    }
    assert(!can_produce_flag);
    if (!eof) {
      int cons = val->get();
      //printf("Consumer: %d\n", cons);
      *result += cons;
      can_produce_flag = true;
      pthread_cond_broadcast(&can_produce);
    } else {
      //printf("Consumer: EOF\n");
      break;
    }
  }
  pthread_mutex_unlock(&mutex);
  return (void*)result;
}

void* consumer_interruptor_routine(void* arg) {
  pthread_mutex_lock(&mutex);
  while (!can_produce_flag) {
    pthread_cond_wait(&can_produce, &mutex);
  }
  pthread_mutex_unlock(&mutex);
  //printf("Int: Started");

  while(1) {
    pthread_cancel(consumer);
  }
}

int run_threads() {
    // start 4 threads and wait until they're done
    // return sum of update values seen by consumer
  Value *val = new Value();
  eof = 0;
  can_produce_flag = false;

  pthread_create(&producer, NULL, producer_routine, val);
  pthread_create(&interruptor, NULL, consumer_interruptor_routine, NULL);
  pthread_create(&consumer, NULL, consumer_routine, val);

  pthread_setname_np(producer, "producer");
  pthread_setname_np(interruptor, "interruptor");
  pthread_setname_np(consumer, "consumer");

  void *result;
  pthread_join(producer, NULL);
  pthread_join(consumer, &result);
  pthread_cancel(interruptor);
  return *((int*)result);
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
