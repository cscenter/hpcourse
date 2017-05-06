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

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumer_start_cond = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_ready_cond = PTHREAD_COND_INITIALIZER;
pthread_cond_t producer_ready_cond = PTHREAD_COND_INITIALIZER;

bool consumer_is_started = false;
bool consumer_is_ready = false;
bool consumer_is_finished = false;
bool producer_is_ready = false;
bool producer_is_finished = false;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process

    pthread_mutex_lock(&mutex);
    while (!consumer_is_started)
    {
        pthread_cond_wait(&consumer_start_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);
    Value *value = (Value *) arg;
    int number = 0;
    while (std::cin >> number)
    {
        pthread_mutex_lock(&mutex);
        value->update(number);
        producer_is_ready = true;
        pthread_cond_signal(&producer_ready_cond);
        while (!consumer_is_ready)
        {
            pthread_cond_wait(&consumer_ready_cond, &mutex);
        }
        consumer_is_ready = false;
        pthread_mutex_unlock(&mutex);
    }
    pthread_mutex_lock(&mutex);
    producer_is_finished = true;
    producer_is_ready = true;
    pthread_cond_signal(&producer_ready_cond);
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result
    
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    pthread_mutex_lock(&mutex);
    consumer_is_started = true;
    pthread_cond_broadcast(&consumer_start_cond);
    pthread_mutex_unlock(&mutex);
    Value *value = (Value *) arg;
    int *sum = new int(0);
    while (true)
    {
        pthread_mutex_lock(&mutex);
        while (!producer_is_ready)
        {
            pthread_cond_wait(&producer_ready_cond, &mutex);
        }
        producer_is_ready = false;
        if (producer_is_finished)
        {
            consumer_is_finished = true;
            pthread_mutex_unlock(&mutex);
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            pthread_exit((void *) sum);
        }
        else
        {
            *sum += value->get();
            consumer_is_ready = true;
            pthread_cond_signal(&consumer_ready_cond);
            pthread_mutex_unlock(&mutex);
        }
    }
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start

    // interrupt consumer while producer is running

    pthread_mutex_lock(&mutex);
    while (!consumer_is_started)
    {
        pthread_cond_wait(&consumer_start_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);
    pthread_t *consumer = (pthread_t *) arg;
    while(!consumer_is_finished)
    {
        pthread_cancel(*consumer);
    }
    pthread_exit(NULL);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    
    Value value;
    int *sum;
    pthread_t producer;
    pthread_t consumer;
    pthread_t interruptor;
    pthread_create(&producer, NULL, producer_routine, (void *) &value);
    pthread_create(&consumer, NULL, consumer_routine, (void *) &value);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &consumer);
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &sum);
    pthread_join(interruptor, NULL);
    int result = *sum;
    delete sum;
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
