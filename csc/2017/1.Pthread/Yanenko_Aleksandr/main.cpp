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

pthread_t producer_thread, consumer_thread, interruptor_thread;
bool consumer_started = false,
     value_updated = false,
     value_consumed = false,
     producer_finished = false,
     consumer_finished = false;
pthread_cond_t cond_consumer_started,
               cond_value_updated,
               cond_value_consumed;
pthread_mutex_t m;

void* producer_routine(void* arg) {
    Value * v = (Value*) arg;

    // Wait for consumer to start
    pthread_mutex_lock(&m);
    while (!consumer_started) {
        pthread_cond_wait(&cond_consumer_started, &m);
    }
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    std::vector<int> values;
    int int_value;
    while (std::cin >> int_value) {
        values.push_back(int_value);
    }
    pthread_mutex_unlock(&m);

    for (int i = 0; i < values.size(); ++i) {
        pthread_mutex_lock(&m);
        v->update(values[i]);
        if (i == values.size() - 1) {
            producer_finished = true;
        }
        value_updated = true;
        pthread_cond_signal(&cond_value_updated);
        while (!value_consumed) {
            pthread_cond_wait(&cond_value_consumed, &m);
        }
        value_consumed = false;
        pthread_mutex_unlock(&m);
    }
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    pthread_mutex_lock(&m);
    consumer_started = true;
    pthread_cond_broadcast(&cond_consumer_started);
    pthread_mutex_unlock(&m);

    Value * v = (Value*) arg;
    // allocate value for result
    int * sum = new int(0);

    // for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&m);
        while (!value_updated) {
            pthread_cond_wait(&cond_value_updated, &m);
        }
        value_updated = false;
        *sum += v->get();
        value_consumed = true;
        pthread_cond_signal(&cond_value_consumed);
        pthread_mutex_unlock(&m);
        if (producer_finished) {
            break;
        }
    }
    
    consumer_finished = true;
    // return pointer to result
    return (void*)sum;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_mutex_lock(&m);
    while (!consumer_started) {
        pthread_cond_wait(&cond_consumer_started, &m);
    }
    pthread_mutex_unlock(&m);
    // interrupt consumer while producer is running
    while (!consumer_finished) {
        pthread_mutex_lock(&m);
        pthread_cancel(consumer_thread);
        pthread_mutex_unlock(&m);
    }

    return 0;
}

int run_threads() {
    pthread_mutex_init(&m, NULL);

    Value v;
    // start 3 threads and wait until they're done
    pthread_cond_init(&cond_consumer_started, NULL);
    pthread_cond_init(&cond_value_updated, NULL);
    pthread_cond_init(&cond_value_consumed, NULL);
    pthread_create(&producer_thread, NULL, producer_routine, (void*)&v);
    pthread_create(&consumer_thread, NULL, consumer_routine, (void*)&v);
    // pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, NULL);

    int * result_ptr;
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, (void**)&result_ptr);
    int result = *result_ptr;
    delete result_ptr;
    // pthread_join(&interruptor_thread, NULL);

    pthread_mutex_destroy(&m);
    pthread_cond_destroy(&cond_consumer_started);
    pthread_cond_destroy(&cond_value_updated);
    pthread_cond_destroy(&cond_value_consumed);
    // return sum of update values seen by consumer

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
