#include <pthread.h>
#include <iostream>
#include <fstream>
#include <unistd.h>
#include <iterator>
#include <vector>
#include <sstream>

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
pthread_cond_t consumer_cv = PTHREAD_COND_INITIALIZER;
pthread_cond_t producer_cv = PTHREAD_COND_INITIALIZER;

bool value_ready = false;
bool producer_started = false;
bool consumer_started = false;

void wait_for_consumer() {
    pthread_mutex_lock(&mutex);
    while (!consumer_started) {
        pthread_cond_wait(&consumer_cv, &mutex);
    }
    pthread_mutex_unlock(&mutex);
}

void *producer_routine(void *arg) {
    // Wait for consumer to start
    wait_for_consumer();

    // Read data, loop through each value
    Value *value = (Value *) arg;
    int n;
    while (std::cin >> n) {
        pthread_mutex_lock(&mutex);
        // wait for consumer to process
        while (value_ready) {
            pthread_cond_wait(&producer_cv, &mutex);
        }
        // update the value, notify consumer
        value->update(n);
        value_ready = true;
        pthread_cond_signal(&producer_cv);
        pthread_mutex_unlock(&mutex);
    }
    producer_started = false;

    pthread_exit(0);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    // notify about start
    pthread_mutex_lock(&mutex);
    consumer_started = true;
    pthread_cond_broadcast(&consumer_cv);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    Value *value = (Value *) arg;
    int *sum = new int;
    *sum = 0;

    // for every update issued by producer
    while (producer_started) {
        pthread_mutex_lock(&mutex);
        // wait until value is ready to be consumed
        while (!value_ready) {
            pthread_cond_wait(&producer_cv, &mutex);
        }

        // read the value, add to sum
        *sum += value->get();
        value_ready = false;
        // notify producer we are ready for the next value
        pthread_cond_signal(&producer_cv);
        pthread_mutex_unlock(&mutex);
    }
    consumer_started = false;

    // return pointer to result
    pthread_exit((void *) sum);
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    wait_for_consumer();

    pthread_t *consumer = (pthread_t *) arg;
    // interrupt consumer while producer is running
    while (producer_started) {
        pthread_cancel(*consumer);
    }
    pthread_exit(0);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer

    Value value;
    pthread_t producer;
    pthread_t consumer;
    pthread_t interruptor;

    producer_started = true;
    pthread_create(&consumer, NULL, consumer_routine, (void *) &value);
    pthread_create(&producer, NULL, producer_routine, (void *) &value);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &consumer);

    pthread_join(producer, NULL);
    pthread_join(interruptor, NULL);
    int *sum;
    pthread_join(consumer, (void **) &sum);
    int result = *sum;
    delete sum;
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}