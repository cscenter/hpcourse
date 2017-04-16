#include <pthread.h>  
#include <iostream>

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

pthread_mutex_t value_lock;

bool allread;
pthread_mutex_t allread_lock;

bool may_produce;
pthread_mutex_t may_produce_lock;
pthread_cond_t may_produce_cond;

bool consumer_started;
pthread_mutex_t consumer_start_lock;
pthread_cond_t consumer_start_cond;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    // Why?

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int data;

    while (std::cin >> data) {

        pthread_mutex_lock(&may_produce_lock);
        while (!may_produce) {
            pthread_cond_wait(&may_produce_cond, &may_produce_lock);  // Consumer has started or read
        }
        pthread_mutex_unlock(&may_produce_lock);

        pthread_mutex_lock(&value_lock);
        ((Value*)arg)->update(data);
        pthread_mutex_unlock(&value_lock);

        pthread_mutex_lock(&may_produce_lock);
        may_produce = false;
        pthread_mutex_unlock(&may_produce_lock);

        pthread_cond_signal(&may_produce_cond);
    }

    // Wait for consumer's final read
    pthread_mutex_lock(&may_produce_lock);
    while (!may_produce) {
        pthread_cond_wait(&may_produce_cond, &may_produce_lock);
    }
    pthread_mutex_unlock(&may_produce_lock);

    pthread_mutex_lock(&allread_lock);
    allread = true;
    pthread_mutex_unlock(&allread_lock);

    pthread_mutex_lock(&may_produce_lock);
    may_produce = false;
    pthread_mutex_unlock(&may_produce_lock);
    pthread_cond_signal(&may_produce_cond);

    return 0;
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    // notify about start
    pthread_mutex_lock(&consumer_start_lock);
    consumer_started = true;
    pthread_mutex_unlock(&consumer_start_lock);
    pthread_cond_broadcast(&consumer_start_cond);

    // allocate value for result
    int *result = new int(0);
    // for every update issued by producer, read the value and add to sum
    while (true) {

        pthread_mutex_lock(&may_produce_lock);
        while (may_produce) {
            pthread_cond_wait(&may_produce_cond, &may_produce_lock);
        }
        pthread_mutex_unlock(&may_produce_lock);

        pthread_mutex_lock(&allread_lock);
        if (allread) {
            pthread_mutex_unlock(&allread_lock);
            break;
        }
        pthread_mutex_unlock(&allread_lock);


        pthread_mutex_lock(&value_lock);
        *result += ((Value *)arg)->get();
        pthread_mutex_unlock(&value_lock);

        pthread_mutex_lock(&may_produce_lock);
        may_produce = true;
        pthread_mutex_unlock(&may_produce_lock);

        pthread_cond_signal(&may_produce_cond);
    }
    // return pointer to result
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
    return (void*)result;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_t *consumer = (pthread_t*)arg;
    pthread_mutex_lock(&consumer_start_lock);
    while (!consumer_started) {
        pthread_cond_wait(&consumer_start_cond, &consumer_start_lock);
    }
    pthread_mutex_unlock(&consumer_start_lock);

    // interrupt consumer while producer is running
    while (true) {
        pthread_mutex_lock(&allread_lock);
        if (!allread) {
            pthread_mutex_unlock(&allread_lock);
            pthread_cancel(*consumer);
        }
        else {
            pthread_mutex_unlock(&allread_lock);
            return 0;
        }
    }
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    Value val;

    may_produce = true;
    consumer_started = false;
    allread = false;

    pthread_mutex_init(&value_lock, NULL);
    pthread_mutex_init(&allread_lock, NULL);
    pthread_mutex_init(&may_produce_lock, NULL);
    pthread_mutex_init(&consumer_start_lock, NULL);
    pthread_cond_init(&may_produce_cond, NULL);
    pthread_cond_init(&consumer_start_cond, NULL);

    pthread_t producer, consumer, interruptor;
    pthread_create(&producer, NULL, producer_routine, (void*)&val);
    pthread_create(&consumer, NULL, consumer_routine, (void*)&val);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void*)&consumer);

    int *psum;
    pthread_join(producer, NULL);
    pthread_join(interruptor, NULL);
    pthread_join(consumer, (void**)&psum);

    int sum = *psum;
    delete psum;

    pthread_cond_destroy(&may_produce_cond);
    pthread_cond_destroy(&consumer_start_cond);
    pthread_mutex_destroy(&value_lock);
    pthread_mutex_destroy(&allread_lock);
    pthread_mutex_destroy(&may_produce_lock);
    pthread_mutex_destroy(&consumer_start_lock);
    
    return sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
