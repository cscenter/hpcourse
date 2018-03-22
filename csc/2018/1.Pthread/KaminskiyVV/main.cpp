#include <pthread.h>
#include <iostream>
#include <vector>
#include <string>
#include <iterator>

std::vector<int> split(const std::string &text, char sep) {
    std::vector<int> args;
    std::size_t start = 0, end = 0;
    while ((end = text.find(sep, start)) != std::string::npos) {
        args.push_back(std::stoi(text.substr(start, end - start)));
        start = end + 1;
    }
    args.push_back(std::stoi(text.substr(start)));
    return args;
}

class Data {
    int value;
    pthread_mutex_t lock;
    pthread_cond_t sync_producers;
public:
    bool is_updated;
    pthread_cond_t sync_consumers;

    Data() : value(0), is_updated(false),
             lock(PTHREAD_MUTEX_INITIALIZER),
             sync_producers(PTHREAD_COND_INITIALIZER),
             sync_consumers(PTHREAD_COND_INITIALIZER) {}

    void produce(int _value) {
        pthread_mutex_lock(&lock);
        while (is_updated) {
            pthread_cond_wait(&sync_producers, &lock);
        }
        value = _value;
        is_updated = true;

        pthread_cond_broadcast(&sync_consumers);
        pthread_mutex_unlock(&lock);
    }

    int consume() {
        int return_value = 0;
        pthread_mutex_lock(&lock);
        while (!is_updated) {
            pthread_cond_wait(&sync_consumers, &lock);
        }

        if (is_updated) {
            return_value = value;
            is_updated = false;
        }
        pthread_cond_broadcast(&sync_producers);
        pthread_mutex_unlock(&lock);
        return return_value;
    }
};

class State{
    bool value;
    pthread_mutex_t lock;

public:
    State(): value(true),
             lock(PTHREAD_MUTEX_INITIALIZER) {}

    bool get(){
        bool local_value;
        pthread_mutex_lock(&lock);
        local_value = value;
        pthread_mutex_unlock(&lock);
        return local_value;
    }

    void set(bool is_working){
        pthread_mutex_lock(&lock);
        value = is_working;
        pthread_mutex_unlock(&lock);
    }
};

State is_working;
Data data;

void wait_consumer(bool is_consumer) {
    static pthread_mutex_t sync_lock = PTHREAD_MUTEX_INITIALIZER;
    static pthread_cond_t sync_cond = PTHREAD_COND_INITIALIZER;
    static bool is_consumer_working = false;

    pthread_mutex_lock(&sync_lock);
    if (is_consumer) {
        is_consumer_working = true;
    }

    if (!is_consumer_working) {
        pthread_cond_wait(&sync_cond, &sync_lock);
    } else {
        pthread_cond_broadcast(&sync_cond);
    }
    pthread_mutex_unlock(&sync_lock);
}


void *producer_routine(void *arg) {
    std::string input;
    // Wait for consumer to start
    wait_consumer(false);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    std::getline(std::cin, input);
    std::vector<int> args = split(input, ' ');

    for (int n : args) {
        data.produce(n);
    }
    is_working.set(false);
    // Release consumers
    pthread_cond_broadcast(&data.sync_consumers);
    return nullptr;
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    // notify about start
    wait_consumer(true);
    // allocate value for result
    auto *sum = (int *) arg;
    *sum = 0;
    // for every update issued by producer, read the value and add to sum
    // return pointer to result
    while (is_working.get()) {
        *sum += data.consume();
    }
    return nullptr;
}

void *consumer_interruptor_routine(void *arg) {
    pthread_t *consumer_t = (pthread_t *) arg;
    // wait for consumer to start
    wait_consumer(false);
    // interrupt consumer while producer is running
    while (is_working.get()) {
        pthread_cancel(*consumer_t);
    }
    return nullptr;
}

int run_threads() {
    int result;
    pthread_t producer_t, consumer_t, interruptor_t;

    // start 3 threads and wait until they're done
    pthread_create(&producer_t, nullptr, producer_routine, nullptr);
    pthread_create(&consumer_t, nullptr, consumer_routine, &result);
    pthread_create(&interruptor_t, nullptr, consumer_interruptor_routine, &consumer_t);

    pthread_join(interruptor_t, nullptr);
    pthread_join(consumer_t, nullptr);
    pthread_join(producer_t, nullptr);
    // return sum of update values seen by consumer
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}