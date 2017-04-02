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
pthread_mutex_t mutex;
pthread_barrier_t barrier;
pthread_cond_t time_to_produce;
pthread_cond_t time_to_consume;

volatile bool updated = false;
volatile bool consumer_started = false;
volatile bool producer_stopped = false;

void *producer_routine(void *arg) {
    // Wait for consumer to start
    pthread_barrier_wait(&barrier);

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *data = (Value *) arg;

    int val;
    while (std::cin >> val) {
        pthread_mutex_lock(&mutex);
        while (updated) {
            pthread_cond_wait(&time_to_produce, &mutex);
        }

        data->update(val);

        updated = true;

        pthread_cond_signal(&time_to_consume);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    producer_stopped = true;
    updated = true;
    pthread_cond_signal(&time_to_consume);
    pthread_mutex_unlock(&mutex);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    Value *data = (Value *) arg;
    int *sum = new int(0);

    // notify about start
    consumer_started = true;

    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    while (!producer_stopped) {
        pthread_mutex_lock(&mutex);
        while (!updated) {
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
    consumer_started = false;

    // return pointer to result
    return (void *) sum;
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    pthread_barrier_wait(&barrier);

    // interrupt consumer while producer is running
    while (consumer_started) {
        if (updated) {
            pthread_cancel(consumer);
        }
    }
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer

    pthread_barrier_init(&barrier, NULL, 2);
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