#include <pthread.h>
#include <iostream>

class Value {
    int _value;

public:
    Value(int value) : _value(value) {}

    int get() const {
        return _value;
    }

    void update(int value) {
        _value = value;
    }
};

pthread_mutex_t m = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond_producer = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_consumer = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_interruptor = PTHREAD_COND_INITIALIZER;

volatile bool is_consumer_started = false;
volatile bool is_producer_ready = false;
volatile bool is_consumer_ready = false;
volatile bool is_producer_finished = false;
volatile bool is_consumer_finished = false;

void *producer_routine(void *arg) {
    int in_value;
    Value* value = (Value*)arg;

    while (std::cin >> in_value) {
        pthread_mutex_lock(&m);

        value->update(in_value);
        is_producer_ready = true;
        pthread_cond_signal(&cond_producer);

        while (!is_consumer_ready) pthread_cond_wait(&cond_consumer, &m);
        is_consumer_ready = false;

        pthread_mutex_unlock(&m);
    }

    is_producer_ready = true;
    pthread_cond_signal(&cond_producer);

    is_producer_finished = true;

    pthread_exit(NULL);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    is_consumer_started = true;
    pthread_mutex_lock(&m);
    pthread_cond_signal(&cond_interruptor);
    pthread_mutex_unlock(&m);

    int sum = 0;
    Value* value = (Value*)arg;

    while (true) {
        pthread_mutex_lock(&m);

        while(!is_producer_ready) pthread_cond_wait(&cond_producer, &m);
        is_producer_ready = false;

        if (is_producer_finished) break;

        sum += value->get();
        is_consumer_ready = true;
        pthread_cond_signal(&cond_consumer);

        pthread_mutex_unlock(&m);
    }

    is_consumer_finished = true;

    pthread_exit((void*)sum);
}

void *interruptor_routine(void *arg) {
    pthread_mutex_lock(&m);
    while (!is_consumer_started) pthread_cond_wait(&cond_interruptor, &m);
    pthread_mutex_unlock(&m);

    auto consumer_thread = (pthread_t)arg;

    while (!is_consumer_finished) {
        pthread_cancel(consumer_thread);
    }

    pthread_exit(NULL);
}

int run_threads() {
    pthread_t producer_thread;
    pthread_t consumer_thread;
    pthread_t interruptor_thread;

    Value value = 0;

    pthread_create(&producer_thread, NULL, producer_routine, &value);
    pthread_create(&consumer_thread, NULL, consumer_routine, &value);
    pthread_create(&interruptor_thread, NULL, interruptor_routine, &consumer_thread);

    int result;
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, (void**)&result);
    pthread_join(interruptor_thread, NULL);

    pthread_mutex_destroy(&m);
    pthread_cond_destroy(&cond_producer);
    pthread_cond_destroy(&cond_consumer);
    pthread_cond_destroy(&cond_interruptor);

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
}