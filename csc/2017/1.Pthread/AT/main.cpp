#include <pthread.h>
#include <iostream>
#include <vector>

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
bool consumer_start = false;
bool consumer_ready = false;
bool producer_ready = false;
bool producer_finished = false;


void *producer_routine(void *arg) {
    // Wait for consumer to start
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process

    pthread_mutex_lock(&mutex);
    while (!consumer_start) {
        pthread_cond_wait(&consumer_start_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    Value *value = static_cast<Value *>(arg);
    int number;
    std::vector<int> values;
    while (std::cin >> number) {
        pthread_mutex_lock(&mutex);
        value->update(number);
        //std::cout << "Producer: " << number << std::endl;
        producer_ready = true;
        pthread_cond_signal(&producer_ready_cond);
        while (!consumer_ready) {
            pthread_cond_wait(&consumer_ready_cond, &mutex);
        }
        consumer_ready = false;
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    producer_finished = true;
    producer_ready = true;
    pthread_cond_signal(&producer_ready_cond);
    pthread_mutex_unlock(&mutex);

    pthread_exit(EXIT_SUCCESS);
}

void *consumer_routine(void *arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    pthread_mutex_lock(&mutex);
    consumer_start = true;
    pthread_cond_broadcast(&consumer_start_cond);
    pthread_mutex_unlock(&mutex);

    Value *value = static_cast<Value *>(arg);
    int *sum = new int;
    while (true) {
        pthread_mutex_lock(&mutex);
        while (!producer_ready) {
            pthread_cond_wait(&producer_ready_cond, &mutex);
        }
        producer_ready = false;

        if (producer_finished) {
            pthread_mutex_unlock(&mutex);
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            pthread_exit((void *) sum);
        } else {
            *sum += value->get();
            //std::cout << "Consumer: " << *sum << std::endl;
            consumer_ready = true;
            pthread_cond_signal(&consumer_ready_cond);
            pthread_mutex_unlock(&mutex);
        }
    }
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    // interrupt consumer while producer is running

    pthread_mutex_lock(&mutex);
    while (!consumer_start) {
        pthread_cond_wait(&consumer_start_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    pthread_t *consumer_thread = static_cast<pthread_t *>(arg);
    while(!pthread_cancel(*consumer_thread)) {
    }
    //std::cout << "Consumer was dead" << std::endl;
    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    Value value;
    pthread_t producer_thread;
    pthread_t consumer_thread;
    pthread_t interruptor_thread;

    int *sum;
    if (pthread_create(&producer_thread, NULL, producer_routine, (void *) &value)) {
        std::cout << "Error while creating producer thread" << std::endl;
        return 1;
    }
    if (pthread_create(&consumer_thread, NULL, consumer_routine, (void *) &value)) {
        std::cout << "Error while creating consumer thread" << std::endl;
        return 1;
    }
    if (pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, (void *) &consumer_thread)) {
        std::cout << "Error while creating interruptor thread" << std::endl;
        return 1;
    }

    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, (void **) &sum);
    pthread_join(interruptor_thread, NULL);

    int sum_res = *sum;
    delete sum;
    return sum_res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}