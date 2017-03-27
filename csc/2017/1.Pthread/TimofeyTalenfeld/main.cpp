#include <pthread.h>  
#include <iostream>
#include <map>

enum thread_type {
    PRODUCER = 0,
    CONSUMER = 1,
    INTERRUPTOR = 2
};

enum thread_state {
    STOPPED,
    RUNNING
};

class thread {
public:
    thread(void * routine, void * params) : _state(STOPPED), _routine(routine), _params(params) {
        pthread_cond_init(&_is_started, nullptr);
    }

    void run() {
        pthread_create(&_pthread, nullptr, (void *(*)(void *)) _routine, _params);
    }

    pthread_t get() const {
        return _pthread;
    }

    void notify_thread_started(pthread_mutex_t * mutex) {
        pthread_mutex_lock(mutex);
        _state = RUNNING;
        pthread_cond_broadcast(&_is_started);
        pthread_mutex_unlock(mutex);
    }

    void unsafe_notify_thread_stopped(pthread_mutex_t * mutex) {
        _state = STOPPED;
        pthread_cond_broadcast(&_is_started);
    }

    void notify_thread_stopped(pthread_mutex_t * mutex) {
        pthread_mutex_lock(mutex);
        _state = STOPPED;
        pthread_cond_broadcast(&_is_started);
        pthread_mutex_unlock(mutex);
    }

    bool is_running() {
        return _state == RUNNING;
    }

    void set_cancel_state_enabled(bool enabled) {
        int state = enabled ? PTHREAD_CANCEL_ENABLE : PTHREAD_CANCEL_DISABLE;
        pthread_setcancelstate(state, nullptr);
    }

    void wait_for_running(pthread_mutex_t * mutex) {
        pthread_mutex_lock(mutex);
        while (_state != RUNNING) {
            pthread_cond_wait(&_is_started, mutex);
        }
        pthread_mutex_unlock(mutex);
    }

    ~thread() {
        pthread_cond_destroy(&_is_started);
    }

    void cancel() {
        pthread_cancel(_pthread);
    }

private:

    thread(const thread &) {}

    pthread_t _pthread;
    pthread_cond_t _is_started;

    thread_state _state;
    void * _routine;
    void * _params;
};

class thread_pool {
public:
    thread_pool() {}

    thread* get(thread_type type) const {
        return &(*(threads.at(type)));
    }

    bool create_thread(thread_type type, void * routine, void * params) {
        if (threads.count(type)) {
            return false;
        }
        threads.emplace(std::make_pair(type, std::shared_ptr<thread>(new thread(routine, params))));
        return true;
    }

    bool run_thread(thread_type type) {
        if (!threads.count(type)) {
            return false;
        }
        get(type)->run();
        return true;
    }

    void run_threads() {
        std::for_each(
            threads.begin(), threads.end(),
            [this] (std::pair<unsigned, std::shared_ptr<thread>> pair) {
                run_thread(static_cast<thread_type>(pair.first));
            }
        );
    }

private:
    std::map<unsigned, std::shared_ptr<thread>> threads;
};

enum exchange_state {
    IDLE,
    EXCHANGING
};

class exchanger {
public:
    exchanger() : state(IDLE) {
        pthread_cond_init(&_is_exchanging, nullptr);
    }

    void init_exchanging() {
        state = EXCHANGING;
        pthread_cond_signal(&_is_exchanging);
    }

    void start_exchanging(pthread_mutex_t * mutex) {
        pthread_mutex_lock(mutex);
    }

    void wait_for_exchange_finished(pthread_mutex_t * mutex) {
        while (state != IDLE) {
            pthread_cond_wait(&_is_exchanging, mutex);
        }
    }

    void wait_for_exchange_started(pthread_mutex_t * mutex) {
        while (state != EXCHANGING) {
            pthread_cond_wait(&_is_exchanging, mutex);
        }
    }

    void exchange() {
        state = IDLE;
        pthread_cond_signal(&_is_exchanging);
    }

    void end_exchanging(pthread_mutex_t * mutex) {
        pthread_mutex_unlock(mutex);
    }

    ~exchanger() {
        pthread_cond_destroy(&_is_exchanging);
    }

private:

    exchanger(const exchanger &) { }

    pthread_cond_t _is_exchanging;
    exchange_state state;
};

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

thread_pool _threads;

pthread_mutex_t _mutex;

exchanger _exchanger;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    _threads.get(CONSUMER)->wait_for_running(&_mutex);
    _threads.get(PRODUCER)->notify_thread_started(&_mutex);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int value = 0;
    while (std::cin >> value) {
        _exchanger.start_exchanging(&_mutex);
        _exchanger.init_exchanging();
        ((Value *) arg)->update(value);
        _exchanger.wait_for_exchange_finished(&_mutex);
        _exchanger.end_exchanging(&_mutex);
    }

    _exchanger.start_exchanging(&_mutex);
    _exchanger.init_exchanging();

    _threads.get(PRODUCER)->unsafe_notify_thread_stopped(&_mutex);

    _exchanger.wait_for_exchange_finished(&_mutex);
    _exchanger.end_exchanging(&_mutex);

    return nullptr;
}

void* consumer_routine(void* arg) {
    // notify about start
    _threads.get(CONSUMER)->set_cancel_state_enabled(false);
    _threads.get(CONSUMER)->notify_thread_started(&_mutex);

    // allocate value for result
    int * sum = new int(0);
    // for every update issued by producer, read the value and add to sum
    while (true) {
        _exchanger.start_exchanging(&_mutex);
        _exchanger.wait_for_exchange_started(&_mutex);
        if (!_threads.get(PRODUCER)->is_running()) {
            _exchanger.exchange();
            _exchanger.end_exchanging(&_mutex);
            break;
        }

        *sum += ((Value *) arg)->get();
        _exchanger.exchange();
        _exchanger.end_exchanging(&_mutex);
    }

    _threads.get(CONSUMER)->set_cancel_state_enabled(true);

    // return pointer to result
    _threads.get(CONSUMER)->notify_thread_stopped(&_mutex);

    return sum;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    _threads.get(CONSUMER)->wait_for_running(&_mutex);

    // interrupt consumer while producer is running
    while (_threads.get(PRODUCER)->is_running()) {
        _threads.get(CONSUMER)->cancel();
    }

    _threads.get(INTERRUPTOR)->notify_thread_stopped(&_mutex);

    return nullptr;
}

int run_threads() {
    // start 3 _threads and wait until they're done

    std::unique_ptr<Value> value(new Value());

    pthread_mutex_init(&_mutex, nullptr);

    _threads.create_thread(PRODUCER, (void *) producer_routine, (void *) &*value);
    _threads.create_thread(CONSUMER, (void *) consumer_routine, (void *) &*value);
    _threads.create_thread(INTERRUPTOR, (void *) consumer_interruptor_routine, nullptr);

    _threads.run_threads();

    // return sum of update values seen by consumer

    std::shared_ptr<int> result(new int(0));

    pthread_join(_threads.get(PRODUCER)->get(), nullptr);
    pthread_join(_threads.get(CONSUMER)->get(), (void **) &result);
    pthread_join(_threads.get(INTERRUPTOR)->get(), nullptr);

    pthread_mutex_destroy(&_mutex);

    return *result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}