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

pthread_cond_t data_cond;
pthread_cond_t start_cond;
pthread_mutex_t mutex;

enum Status { CONS_STARTED, UPDATED, NEEDS_UPDATE, PROD_DONE };
Status status = UPDATED;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (status != CONS_STARTED)
        pthread_cond_wait(&start_cond, &mutex);
    pthread_mutex_unlock(&mutex);

    int n;
    Value * value = (Value *) arg;
    std::vector<int> data;

    // Read data
    while (std::cin >> n)
        data.push_back(n);

    // loop through each value and update the value, notify consumer, wait for consumer to process
    for (int n : data) {
        pthread_mutex_lock(&mutex);

        value->update(n);
        status = NEEDS_UPDATE;

        pthread_cond_signal(&data_cond);
        while (status != UPDATED)
            pthread_cond_wait(&data_cond, &mutex);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    status = PROD_DONE;
    pthread_cond_signal(&data_cond);
    pthread_mutex_unlock(&mutex);

    return NULL;
}

void* consumer_routine(void* arg) {
    // defend from interruptor
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    // notify about start
    pthread_mutex_lock(&mutex);
    status = CONS_STARTED;
    pthread_cond_broadcast(&start_cond);
    pthread_mutex_unlock(&mutex);

    Value * value = (Value *) arg;
    // allocate value for result
    int * sum = new int(0);

    // for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&mutex);

        while (status != NEEDS_UPDATE && status != PROD_DONE)
            pthread_cond_wait(&data_cond, &mutex);

        if (status == PROD_DONE)
            break;

        *sum += value->get();
        status = UPDATED;

        pthread_cond_signal(&data_cond);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_unlock(&mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE,  NULL);

    // return pointer to result
    return (void *) sum;
}

void* consumer_interruptor_routine(void* arg) {
    pthread_t * consumer = (pthread_t *) arg;

    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (status != CONS_STARTED)
        pthread_cond_wait(&start_cond, &mutex);
    pthread_mutex_unlock(&mutex);

    // interrupt
    while (status != PROD_DONE)
        pthread_cancel(*consumer);

    return NULL;
}

int run_threads() {
    pthread_t producer, consumer, interruptor;
    int * res;
    Value value;

    // start 3 threads and wait until they're done
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&data_cond, NULL);
    pthread_cond_init(&start_cond, NULL);

    pthread_create(&producer, NULL, producer_routine, (void *) &value);
    pthread_create(&consumer, NULL, consumer_routine, (void *) &value);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &consumer);

    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &res);
    pthread_join(interruptor, NULL);

    pthread_cond_destroy(&data_cond);
    pthread_cond_destroy(&start_cond);
    pthread_mutex_destroy(&mutex);

    int sum = *res;
    delete res;

    // return sum of update values seen by consumer
    return sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
