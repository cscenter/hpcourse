#include <pthread.h>  
#include <iostream>
#include <vector>
#include <iterator>
#include <sstream>

pthread_barrier_t wait_consumer_barrier;
pthread_mutex_t value_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t value_cond = PTHREAD_COND_INITIALIZER;
bool value_ready;
bool complete = false;

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

void *producer_routine(void *arg) {
    // Wait for consumer to start
    pthread_barrier_wait(&wait_consumer_barrier);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *value = static_cast<Value *>(arg);
    // read data
    std::string data_str;
    getline(std::cin, data_str);
    std::istringstream stream(data_str);
    std::vector<int> vector = (std::vector<int>(std::istream_iterator<int>(stream),std::istream_iterator<int>()));
    // feed consumer
    std::vector<int>::iterator it = vector.begin();
    value_ready = false;
    while (!complete) {
        pthread_mutex_lock(&value_mutex);
        if (!value_ready) {
            int x = *it;
            value->update(x);
            value_ready = true;
            ++it;
            if(it == vector.end()){
                complete = true;
            }
            pthread_cond_signal(&value_cond);
        }
        pthread_mutex_unlock(&value_mutex);
    }
    pthread_exit(nullptr);
}

void *consumer_routine(void *arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result
    // init
    Value *value = static_cast<Value *>(arg);
    int *sum = new int;
    *sum = 0;
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    // ready
    pthread_barrier_wait(&wait_consumer_barrier);
    // calc
    while (true) {
        pthread_mutex_lock(&value_mutex);
        if (!value_ready) {
            pthread_cond_wait(&value_cond, &value_mutex);
        }
        if(value_ready){
            *sum = *sum + value->get();
            value_ready = false;
        }
        if(complete){
            pthread_mutex_unlock(&value_mutex);
            break;
        }
        pthread_mutex_unlock(&value_mutex);
    }
    // finish
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, nullptr);
    pthread_exit(sum);
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    pthread_barrier_wait(&wait_consumer_barrier);
    // interrupt consumer while producer is running
    auto consumer = (pthread_t*)arg;
    while (!complete) {
        pthread_cancel(*consumer);
    }
    pthread_exit(nullptr);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    pthread_barrier_init(&wait_consumer_barrier, nullptr, 3);
    pthread_t producer_threat, consumer_threat, interruptor_thread;
    Value value;
    pthread_create(&producer_threat, nullptr, producer_routine, &value);
    pthread_create(&consumer_threat, nullptr, consumer_routine, &value);
    pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, &consumer_threat);

    void *result_ptr = nullptr;
    pthread_join(producer_threat, nullptr);
    pthread_join(consumer_threat, &result_ptr);
    pthread_join(interruptor_thread, nullptr);
    int *result_ptr_int = static_cast<int *>(result_ptr);
    int result = *result_ptr_int;
    pthread_barrier_destroy(&wait_consumer_barrier);
    delete result_ptr_int;
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
