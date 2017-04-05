#include <pthread.h>
#include <iostream>
#include <cstdio>


class Value {
public:
    Value(int value = 0) : _value(value) {}

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }

private:
    int _value;
};

pthread_t producer, consumer, consumer_interruptor;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t value_updated_cond;
pthread_cond_t consumer_cond;

bool value_updated = false;
bool producer_stopped = false;
bool consumer_started = false;

void *producer_routine(void *arg) {
    // Wait for consumer to start
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *data = (Value *) arg;
    while (std::cin.peek() != '\n') {
        pthread_mutex_lock(&mutex);
        while (value_updated) {
            pthread_cond_wait(&value_updated_cond, &mutex);
        }
        int val;
        std::cin >> val;
        data->update(val);
        value_updated = true;
        pthread_cond_signal(&value_updated_cond);
        pthread_mutex_unlock(&mutex);
    }
    producer_stopped = true;
}

void *consumer_routine(void *arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    consumer_started = true;

    pthread_mutex_lock(&mutex);
    pthread_cond_signal(&consumer_cond);
    pthread_mutex_unlock(&mutex);

    Value *data = (Value *) arg;
    int *res = new int;
    *res = 0;
    while (!producer_stopped) {
        pthread_mutex_lock(&mutex);
        while (!value_updated) {
            pthread_cond_wait(&value_updated_cond, &mutex);
        }
        *res += data->get();
        value_updated = false;
        pthread_cond_signal(&value_updated_cond);
        pthread_mutex_unlock(&mutex);
    }
    consumer_started = false;
    pthread_exit(res);
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    // interrupt consumer while producer is running
    pthread_mutex_lock(&mutex);
    while (!consumer_started) {
        pthread_cond_wait(&consumer_cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    while (consumer_started) {
        pthread_cancel(consumer);
    }
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    Value data;
    pthread_cond_init(&value_updated_cond, NULL);
    pthread_cond_init(&consumer_cond, NULL);
    pthread_create(&producer, NULL, producer_routine, &data);
    pthread_create(&consumer, NULL, consumer_routine, &data);
    pthread_create(&consumer_interruptor, NULL, consumer_interruptor_routine, NULL);

    int *ref2res = new int;
    int **ref2ref2res = &ref2res;
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) ref2ref2res);
    pthread_join(consumer_interruptor, NULL);
    int res = **ref2ref2res;
    delete ref2res;
    return res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
