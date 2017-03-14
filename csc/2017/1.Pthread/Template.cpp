//
// Created by Alexey Berezin on 9/03/17.
//
#include <pthread.h>
#include <iostream>
#include <cstdlib>

// basic states
const int APP_HAS_BEEN_STARTED = 1 << 0;
const int CONSUMER_HAS_BEEN_STARTED = 1 << 1;
const int VALUE_IS_READY_TO_BE_PROCESSED = 1 << 2;
const int VALUE_HAS_BEEN_PROCESSED = 1 << 3;
const int PRODUCER_HAS_FINISHED = 1 << 4;

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

// state of the app
int state = APP_HAS_BEEN_STARTED;
// mutex
pthread_mutex_t mutex;
// signals
pthread_cond_t signal_from_producer, signal_from_consumer;

void *producer_routine(void *arg) {
    // extract a value
    Value *value = (Value *)arg;

    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (state != CONSUMER_HAS_BEEN_STARTED) {
        pthread_cond_wait(&signal_from_consumer, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    int n;
    // Read data and loop through each value
    while (std::cin >> n) {
        pthread_mutex_lock(&mutex);

        // update the value
        value->update(n);

        // notify consumer
        state = VALUE_IS_READY_TO_BE_PROCESSED;
        pthread_cond_signal(&signal_from_producer);

        // wait for consumer to process
        while (state != VALUE_HAS_BEEN_PROCESSED) {
            pthread_cond_wait(&signal_from_consumer, &mutex);
        }
        pthread_mutex_unlock(&mutex);
    }

    // notify consumer
    pthread_mutex_lock(&mutex);
    state = PRODUCER_HAS_FINISHED;
    pthread_cond_signal(&signal_from_producer);
    pthread_mutex_unlock(&mutex);

    // exit
    pthread_exit(NULL);
}

void *consumer_routine(void *arg) {
    // extract a value
    Value *value = (Value *)arg;

    // notify about start
    pthread_mutex_lock(&mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    state = CONSUMER_HAS_BEEN_STARTED;
    pthread_cond_broadcast(&signal_from_consumer);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    int *sum = new int(0);
    while (1) {
        pthread_mutex_lock(&mutex);

        // for every update issued by producer
        while (state != VALUE_IS_READY_TO_BE_PROCESSED &&
                state != PRODUCER_HAS_FINISHED) {
            pthread_cond_wait(&signal_from_producer, &mutex);
        }

        if (state == PRODUCER_HAS_FINISHED) {
            pthread_mutex_unlock(&mutex);
            break;
        }

        // read the value and add to sum
        *sum += value->get();
        state = VALUE_HAS_BEEN_PROCESSED;
        pthread_cond_signal(&signal_from_consumer);

        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
    pthread_mutex_unlock(&mutex);

    // return pointer to result
    pthread_exit((void*)sum);
}

void *consumer_interruptor_routine(void *arg) {
    // get a consumer
    pthread_t *consumer = (pthread_t*)arg;

    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (state != CONSUMER_HAS_BEEN_STARTED) {
        pthread_cond_wait(&signal_from_consumer, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    // interrupt consumer while producer is running
    while (state != PRODUCER_HAS_FINISHED) {
        pthread_cancel(*consumer);
    }

    // exit
    pthread_exit(NULL);
}

int run_threads() {
    // 3 потока: производитель, покупатель и прерыватель
    pthread_t producer, consumer, interruptor;
    Value value;

    // start 3 threads and wait until they're done
    int error_code = pthread_create(&producer, NULL, &producer_routine, (void*)&value);
    if (error_code) {
        std::cerr << "create producer error: " << error_code << std::endl;
        exit(EXIT_FAILURE);
    }
    error_code = pthread_create(&consumer, NULL, &consumer_routine, (void*)&value);
    if (error_code) {
        std::cerr << "create consumer error: " << error_code << std::endl;
        exit(EXIT_FAILURE);
    }
    error_code = pthread_create(&interruptor, NULL, &consumer_interruptor_routine, (void*)&consumer);
    if (error_code) {
        std::cerr << "create interruptor error: " << error_code << std::endl;
        exit(EXIT_FAILURE);
    }

    // initialise global values
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&signal_from_producer, NULL);
    pthread_cond_init(&signal_from_consumer, NULL);

    int *result;

    // join threads
    pthread_join(producer, NULL);
    pthread_join(consumer, (void**)&result);
    pthread_join(interruptor, NULL);

    // return sum of update values seen by consumer
    int sum = *result;

    pthread_cond_destroy(&signal_from_consumer);
    pthread_cond_destroy(&signal_from_producer);
    pthread_mutex_destroy(&mutex);
    delete result;

    return sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}