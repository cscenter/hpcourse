#include <vector>
#include <iostream>
#include <sstream>
#include <iterator>
#include <algorithm>
#include <cstddef>

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

pthread_mutex_t mutex;
pthread_barrier_t barrier;
pthread_cond_t producer_ready;
pthread_cond_t consumer_ready;

enum State {START, VALUE_PRODUCED, VALUE_CONSUMED, EXIT};

State state = START;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    pthread_barrier_wait(&barrier);

    Value* value = reinterpret_cast<Value*>(arg);

    std::string buf;
    std::getline(std::cin, buf);
    std::istringstream stream(buf);

    std::vector<int> values;
    std::copy(std::istream_iterator<int>(stream),
              std::istream_iterator<int>(),
              std::back_inserter(values));

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    pthread_mutex_lock(&mutex);
    for (auto v: values) {
        value->update(v);
        state = VALUE_PRODUCED;
        pthread_cond_broadcast(&producer_ready);
        while (state != VALUE_CONSUMED) {
            pthread_cond_wait(&consumer_ready, &mutex);
        }
    }
    state = EXIT;
    pthread_cond_broadcast(&producer_ready);
    pthread_mutex_unlock(&mutex);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    // notify about start
    pthread_barrier_wait(&barrier);

    Value* value = reinterpret_cast<Value*>(arg);

    // allocate value for result
    int* res = new int(0);

    // for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&mutex);
        while (state != VALUE_PRODUCED && state != EXIT) {
            pthread_cond_wait(&producer_ready, &mutex);
        }
        if (state == EXIT) {
            pthread_mutex_unlock(&mutex);
            break;
        }
        *res += value->get();
        state = VALUE_CONSUMED;
        pthread_cond_signal(&consumer_ready);
        pthread_mutex_unlock(&mutex);
    }

    // return pointer to result
    return reinterpret_cast<void*>(res);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_barrier_wait(&barrier);

    pthread_t* consumer = reinterpret_cast<pthread_t*>(arg);

    // interrupt consumer while producer is running
    pthread_mutex_lock(&mutex);
    while (state != EXIT) {
        pthread_cancel(*consumer);
        pthread_cond_wait(&producer_ready, &mutex);
    }
    pthread_mutex_unlock(&mutex);
}

int run_threads() {
    pthread_t producer_thread;
    pthread_t consumer_thread;
    pthread_t interruptor_thread;

    static constexpr size_t threads_cnt = 3;

    pthread_mutex_init(&mutex, nullptr);
    pthread_cond_init(&producer_ready, nullptr);
    pthread_cond_init(&consumer_ready, nullptr);
    pthread_barrier_init(&barrier, nullptr, threads_cnt);

    Value value;
    pthread_create(&producer_thread, nullptr, producer_routine, &value);
    pthread_create(&consumer_thread, nullptr, consumer_routine, &value);
    pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, &consumer_thread);

    int* res = nullptr;
    pthread_join(producer_thread, nullptr);
    pthread_join(consumer_thread, reinterpret_cast<void**>(&res));
    pthread_join(interruptor_thread, nullptr);

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&producer_ready);
    pthread_cond_destroy(&consumer_ready);
    pthread_barrier_destroy(&barrier);

    int res_ = *res;
    delete res;

    return res_;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}