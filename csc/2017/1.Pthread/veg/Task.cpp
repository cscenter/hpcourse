#include <iostream>
#include <pthread.h>


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

pthread_mutex_t data_lock;
// maybe I should've add mutex for each flag, but now I don't really need it

pthread_cond_t consumer_started_cond;
pthread_cond_t data_updated_cond;
pthread_cond_t data_ended_cond;

bool consumer_started = false;
bool data_updated = false;
bool data_ended = false;

void set_data_updated(bool value) {
    pthread_mutex_lock(&data_lock);
    data_updated = value;
    pthread_cond_signal(&data_updated_cond);
    pthread_mutex_unlock(&data_lock);
}

void set_data_ended(bool value) {
    pthread_mutex_lock(&data_lock);
    data_ended = value;
    pthread_cond_signal(&data_ended_cond);
    pthread_mutex_unlock(&data_lock);
}

void set_consumer_started(bool value) {
    pthread_mutex_lock(&data_lock);
    consumer_started = value;
    pthread_cond_broadcast(&consumer_started_cond);
    pthread_mutex_unlock(&data_lock);
}

void wait_for_data_updated(bool value) {
    pthread_mutex_lock(&data_lock);
    while (data_updated != value) {
        pthread_cond_wait(&data_updated_cond, &data_lock);
    }
    pthread_mutex_unlock(&data_lock);
}

void wait_for_consumer_started(bool value) {
    pthread_mutex_lock(&data_lock);
    while (consumer_started != value) {
        pthread_cond_wait(&consumer_started_cond, &data_lock);
    }
    pthread_mutex_unlock(&data_lock);
}

void *producer_routine(void *arg) {
    Value *data = (Value *) arg;

    // wait for consumer to start
    wait_for_consumer_started(true);

    // read data, loop through each value
    int n;
    while (std::cin >> n) {
        // wait for consumer to process previous value
        wait_for_data_updated(false);

        // lock mutex to modify data
        pthread_mutex_lock(&data_lock);

        // update value and unlock mutex
        data->update(n);
        pthread_mutex_unlock(&data_lock);

        // notify consumer
        set_data_updated(true);
    }

    // make sure last value is processed (and consumer put data_updated=false)
    wait_for_data_updated(false);

    // notify everyone that data ended
    set_data_ended(true);
    set_data_updated(true);  // not elegant, but consumer waits for this signal

    pthread_exit(NULL);
}

void *consumer_routine(void *arg) {
    // resist to cancelling
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    Value *data = (Value *) arg;

    // notify about start
    set_consumer_started(true);

    // allocate value for result
    int *result = new int(0);

    // for every update issued by producer, read the value and add to sum
    while (!data_ended) {
        wait_for_data_updated(true);  // wait for producer to read new value

        if (data_ended) break;

        *result += data->get();
        set_data_updated(false);
    }

    // return pointer to result
    pthread_exit((void **) result);
}

void *consumer_interrupter_routine(void *arg) {
    pthread_t *consumer = (pthread_t *) arg;

    // wait for consumer to start
    wait_for_consumer_started(true);

    // interrupt consumer while producer is running
    while (!data_ended & !pthread_cancel(*consumer));

    pthread_exit(NULL);
}

int run_threads() {
    pthread_t producer, consumer, consumer_interrupter;
    Value *data = new Value();

    // start threads
    pthread_create(&consumer_interrupter, NULL, consumer_interrupter_routine, (void *) &consumer);
    pthread_create(&producer, NULL, producer_routine, (void *) data);
    pthread_create(&consumer, NULL, consumer_routine, (void *) data);

    // wait until they're done
    int *result;
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &result);
    pthread_join(consumer_interrupter, NULL);

    // return sum of update values seen by consumer
    return *result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}