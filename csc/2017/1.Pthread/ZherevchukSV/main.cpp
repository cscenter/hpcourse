#include <pthread.h>
#include <iostream>

pthread_mutex_t a_mutex = PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
pthread_cond_t prod_cond = PTHREAD_COND_INITIALIZER;
pthread_cond_t cons_cond = PTHREAD_COND_INITIALIZER;
enum class State { RUNNABLE, PRODUCER_EXIT, CONSUMER_STARTED, VALUE_PRODUCED, VALUE_CONSUMED};
State state = State::RUNNABLE;

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

void* producer_routine(void* arg) {
    pthread_mutex_lock(&a_mutex);

    // Wait for consumer to start
    while (state == State::RUNNABLE) {
        pthread_cond_wait(&cons_cond, &a_mutex);
    }

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value* value = (Value*)arg;
    int n;
    while (std::cin >> n) {
        value->update(n);
        state = State::VALUE_PRODUCED;
        pthread_cond_broadcast(&prod_cond);
        while (state != State::VALUE_CONSUMED) {
            pthread_cond_wait(&cons_cond, &a_mutex);
        }
    }

    state = State::PRODUCER_EXIT;
    pthread_cond_broadcast(&prod_cond);

    pthread_mutex_unlock(&a_mutex);
    pthread_exit(EXIT_SUCCESS);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    pthread_mutex_lock(&a_mutex);
    // notify about start
    state = State::CONSUMER_STARTED;
    pthread_cond_broadcast(&cons_cond);

    Value* value = (Value*)arg;
    // allocate value for result
    int* result = new int(0);
    // for every update issued by producer, read the value and add to sum
    while (state != State::PRODUCER_EXIT) {
        while (state != State::VALUE_PRODUCED && state != State::PRODUCER_EXIT) {
            pthread_cond_wait(&prod_cond, &a_mutex);
        }
        if (state == State::VALUE_PRODUCED) {
            *result += value->get();
            state = State::VALUE_CONSUMED;
            pthread_cond_broadcast(&cons_cond);
        }
    }

    pthread_mutex_unlock(&a_mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
    // return pointer to result
    return (void*) result;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_mutex_lock(&a_mutex);
    while (state == State::RUNNABLE) {
        pthread_cond_wait(&cons_cond, &a_mutex);
    }
    pthread_mutex_unlock(&a_mutex);
    // interrupt consumer while producer is running
    pthread_t* p_thread_consumer = (pthread_t*)arg;
    while (state != State::PRODUCER_EXIT) {
        pthread_cancel(*p_thread_consumer);
    }
    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    pthread_t   p_thread_producer;
    pthread_t   p_thread_consumer;
    pthread_t   p_thread_interruptor;
    pthread_attr_t attr;
    void* result;

    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);

    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    Value data;
    pthread_create(&p_thread_producer, &attr, producer_routine, &data);
    pthread_create(&p_thread_consumer, &attr, consumer_routine, &data);
    pthread_create(&p_thread_interruptor, &attr, consumer_interruptor_routine, &p_thread_consumer);

    pthread_join(p_thread_producer, NULL);
    pthread_join(p_thread_consumer, &result);
    pthread_join(p_thread_interruptor, NULL);

    return *(int*) result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}