#include <stdio.h>
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

pthread_mutex_t state_lock = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t event_condition = PTHREAD_COND_INITIALIZER;

bool consumer_started = false;
bool value_produced = false;
bool producer_stopped = false;

void *producer_routine(void *arg) {
    Value *value = (Value *) arg;

    pthread_mutex_lock(&state_lock);
    while (!consumer_started) {
        pthread_cond_wait(&event_condition, &state_lock);
    }
    pthread_mutex_unlock(&state_lock);

    int x;
    while (std::cin >> x) {
        pthread_mutex_lock(&state_lock);
        while (value_produced) {
            pthread_cond_wait(&event_condition, &state_lock);
        }
        value->update(x);
        value_produced = true;
        pthread_cond_broadcast(&event_condition);
        pthread_mutex_unlock(&state_lock);
    }

    pthread_mutex_lock(&state_lock);
    producer_stopped = true;
    pthread_cond_broadcast(&event_condition);
    pthread_mutex_unlock(&state_lock);

    return nullptr;
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    Value *value = (Value *) arg;
    int *result = new int(0);

    pthread_mutex_lock(&state_lock);
    consumer_started = true;
    pthread_cond_broadcast(&event_condition);
    pthread_mutex_unlock(&state_lock);

    while (true) {
        pthread_mutex_lock(&state_lock);
        while (!producer_stopped && !value_produced) {
            pthread_cond_wait(&event_condition, &state_lock);
        }
        if (producer_stopped) {
            pthread_mutex_unlock(&state_lock);
            break;
        }
        *result += value->get();
        value_produced = false;
        pthread_cond_broadcast(&event_condition);
        pthread_mutex_unlock(&state_lock);
    }

    return (void *) result;
}

void *consumer_interruptor_routine(void *arg) {
    pthread_t *consumer_thread = (pthread_t *) arg;

    pthread_mutex_lock(&state_lock);
    while (!consumer_started) {
        pthread_cond_wait(&event_condition, &state_lock);
    }
    while (!producer_stopped) {
        pthread_cancel(*consumer_thread);
        pthread_cond_wait(&event_condition, &state_lock);
    }
    pthread_mutex_unlock(&state_lock);

    return nullptr;
}

int run_threads() {
    void *result;
    Value value;

    pthread_t producer_thread;
    pthread_t consumer_thread;
    pthread_t consumer_interruptor_thread;

    pthread_create(&producer_thread, nullptr, producer_routine, &value);
    pthread_create(&consumer_thread, nullptr, consumer_routine, &value);
    pthread_create(&consumer_interruptor_thread, nullptr, consumer_interruptor_routine, &consumer_thread);

    pthread_join(producer_thread, nullptr);
    pthread_join(consumer_thread, &result);
    pthread_join(consumer_interruptor_thread, nullptr);

    return *(int *) result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
