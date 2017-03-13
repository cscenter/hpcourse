#include <pthread.h>  
#include <iostream>
#include <functional>
#include <sstream>

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


void check_return_code(int code, const char* message) {
    if (code != 0) {
        std::cout << message << std::endl;
    }
}

struct Mutex {
    Mutex(pthread_mutexattr_t *attr = nullptr) {
        check_return_code(
                pthread_mutex_init(&_mutex, attr),
                "error on init mutex"
        );
    }

    virtual ~Mutex() {
        check_return_code(
                pthread_mutex_destroy(&_mutex),
                "error on destroy mutex"
        );
    }

    pthread_mutex_t *get_p() {
        return &_mutex;
    }

    void lock() {
        check_return_code(
                pthread_mutex_lock(&_mutex),
                "error on lock mutex"
        );
    }

    void unlock() {
        check_return_code(
                pthread_mutex_unlock(&_mutex),
                "error on unlock mutex"
        );
    }

private:
    pthread_mutex_t _mutex;

} mutex;

struct conditional_variable {
    conditional_variable(pthread_condattr_t *attr = nullptr) {
        check_return_code(
                pthread_cond_init(&_cond_var, attr),
                "error on init conditional variable"
        );
    }

    virtual ~conditional_variable() {
        check_return_code(
                pthread_cond_destroy(&_cond_var),
                "error on destroy conditional variable"
        );
    }

    void wait(Mutex& m) {
        check_return_code(
                pthread_cond_wait(&_cond_var, m.get_p()),
                "error on wait conditional variable"
        );
    }

    void wait_with_predicate(Mutex& m, std::function<bool()> pred) {
        while(!pred()) {
            wait(m);
        }
    }

    void notify() {
        check_return_code(
                pthread_cond_signal(&_cond_var),
                "error on notify conditional variable"
        );
    }

    void notify_all() {
        check_return_code(
                pthread_cond_broadcast(&_cond_var),
                "error on notify all conditional variable"
        );
    }

private:
    pthread_cond_t _cond_var;

} producer_cond, consumer_cond;

enum class Status {
    NONE,
    CONSUMER_STARTED,
    VALUE_UPDATED,
    VALUE_HANDLED,
    DONE
};

Status status = Status::NONE;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    mutex.lock();
    if (status == Status::NONE) {
        consumer_cond.wait_with_predicate(mutex, [](){return status != Status::NONE;});
    }
    mutex.unlock();

    Value *shared_value = (Value *) arg;

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int number;
    while (std::cin >> number) {
        mutex.lock();
        shared_value->update(number);
        status = Status::VALUE_UPDATED;
        producer_cond.notify_all();
        consumer_cond.wait_with_predicate(mutex, []() {return status == Status::VALUE_HANDLED;});
        // whether it makes sense to unlock mutex for IO ?
        mutex.unlock();
    }

    mutex.lock();
    status = Status::DONE;
    shared_value->update(0);
    mutex.unlock();
    producer_cond.notify_all();

    pthread_exit(nullptr);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    // notify about start
    mutex.lock();
    status = Status::CONSUMER_STARTED;
    consumer_cond.notify_all();

    // allocate value for result
    Value * shared_value = (Value *) arg;
    Value * sum = new Value();

    // for every update issued by producer, read the value and add to sum
    while (true) {
        producer_cond.wait_with_predicate(mutex, []() {return status == Status::VALUE_UPDATED || status == Status::DONE;});
        if (status == Status::DONE) {
            break;
        }
        sum->update(sum->get() + shared_value->get());
        status = Status::VALUE_HANDLED;
        consumer_cond.notify_all();
    }
    mutex.unlock();

    // return pointer to result
    pthread_exit(sum);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    mutex.lock();
    if (status == Status::NONE) {
        consumer_cond.wait_with_predicate(mutex, [](){return status != Status::NONE;});
    }

    pthread_t consumer_thread = *((pthread_t *) arg);

    // interrupt consumer while producer is running
    // we can lock mutex to read status only  when producer and consumer don't work:(
    while (status != Status::DONE) {
        mutex.unlock();
        pthread_cancel(consumer_thread);
        mutex.lock();
    }

    mutex.unlock();
    return nullptr;
}

int run_threads() {
    Value *shared_value = new Value();
    Value *result_value;
    // start 3 threads and wait until they're done
    pthread_t consumer, producer, interrupter;
    check_return_code(
            pthread_create(&producer, nullptr, producer_routine, shared_value),
            "error on create producer thread"
    );
    check_return_code(
            pthread_create(&interrupter, nullptr, consumer_interruptor_routine, &consumer),
            "error on create interrupter thread"
    );
    check_return_code(
            pthread_create(&consumer, nullptr, consumer_routine, shared_value),
            "error on create consumer thread"
    );

    check_return_code(
            pthread_join(producer, nullptr),
            "error on join producer thread"
    );
    check_return_code(
            pthread_join(interrupter, nullptr),
            "error on join producer thread"
    );check_return_code(
            pthread_join(consumer, (void **) &result_value),
            "error on join producer thread"
    );

    // return sum of update values seen by consumer
    int result = result_value->get();
    delete result_value;
    delete shared_value;

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
