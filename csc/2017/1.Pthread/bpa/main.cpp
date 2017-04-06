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

pthread_t producer, consumer, interruptor;
pthread_mutex_t mutex, updated_m, consumer_started_m, producer_stopped_m;
pthread_cond_t time_to_produce;
pthread_cond_t time_to_consume;

bool updated = false;
bool consumer_started = false;
bool producer_stopped = false;

bool safe_read(bool value, pthread_mutex_t *l_mutex) {
    pthread_mutex_lock(l_mutex);
    bool res = value;
    pthread_mutex_unlock(l_mutex);
    return res;
}

void *producer_routine(void *arg) {
    // Wait for consumer to start
    while (!safe_read(consumer_started, &consumer_started_m)) {}

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *data = (Value *) arg;

    int val;
    while (std::cin >> val) {
        pthread_mutex_lock(&mutex);
        while (safe_read(updated, &updated_m)) {
            pthread_cond_wait(&time_to_produce, &mutex);
        }

        data->update(val);

        updated = true;

        pthread_cond_signal(&time_to_consume);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    updated = true;
    producer_stopped = true;
    pthread_cond_signal(&time_to_consume);
    pthread_mutex_unlock(&mutex);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    Value *data = (Value *) arg;
    int *sum = new int(0);

    // notify about start
    pthread_mutex_lock(&consumer_started_m);
    consumer_started = true;
    pthread_mutex_unlock(&consumer_started_m);

    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    while (!safe_read(producer_stopped, &producer_stopped_m)) {
        pthread_mutex_lock(&mutex);
        while (!safe_read(updated, &updated_m)) {
            pthread_cond_wait(&time_to_consume, &mutex);
        }
        if (producer_stopped) {
            pthread_mutex_unlock(&mutex);
            break;
        }
        *sum += data->get();
        updated = false;

        pthread_cond_signal(&time_to_produce);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&consumer_started_m);
    consumer_started = false;
    pthread_mutex_unlock(&consumer_started_m);

    // return pointer to result
    return (void *) sum;
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    while (!safe_read(consumer_started, &consumer_started_m)) {}

    // interrupt consumer while producer is running
    while (!safe_read(producer_stopped, &producer_stopped_m)) {
        if (safe_read(updated, &updated_m)) {
            pthread_cancel(consumer);
        }
    }
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer

    Value data;
    pthread_create(&producer, NULL, producer_routine, &data);
    pthread_create(&consumer, NULL, consumer_routine, &data);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, NULL);

    void *sum;
    pthread_join(producer, NULL);
    pthread_join(consumer, &sum);
    pthread_join(interruptor, NULL);
    return *(int *) sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}