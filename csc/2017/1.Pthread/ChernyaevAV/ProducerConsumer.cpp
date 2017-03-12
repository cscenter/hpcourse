#include <pthread.h>  
#include <iostream>
#include <vector>

class Value {
public:
    Value() : _value(0) {
    }

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }
private:
    int _value;
};

class ProcessValueHandlerCookie {
public:
    ProcessValueHandlerCookie() {
        pthread_mutex_init(&_data_mutex, NULL);

        pthread_cond_init(&_new_value_cond, NULL);
        pthread_cond_init(&_verification_cond, NULL);

        _input_is_closed = false;
        _value_is_verified = true;
    }

    ~ProcessValueHandlerCookie() {
        pthread_mutex_destroy(&_data_mutex);

        pthread_cond_destroy(&_new_value_cond);
        pthread_cond_destroy(&_verification_cond);
    }

    void send_verification() {
        _value_is_verified = true;
        pthread_cond_signal(&_verification_cond);
    }

    void wait_for_new_value() {
        pthread_cond_wait(&_new_value_cond, &_data_mutex);
    }

    void wait_for_verification() {
        pthread_cond_wait(&_verification_cond, &_data_mutex);
    }

    void set_input_closed(bool val) {
        _input_is_closed = val;
    }

    bool is_input_closed() {
        return _input_is_closed;
    }

    void start_sync_block() {
        pthread_mutex_lock(&_data_mutex);
    }

    void end_sync_block() {
        pthread_mutex_unlock(&_data_mutex);
    }

    bool check_value_verified() {
        return _value_is_verified;
    }

    void update(int val ) {
        _value.update(val);
        _value_is_verified = false;
        pthread_cond_signal(&_new_value_cond);
    }

    int get_value() {
        return _value.get();
    }

    void wait_for_consumer_start() {
        while (!_consumer_started);
    }

    void notify_consumer_started() {
        _consumer_started = true;
    }
private:
    pthread_mutex_t _data_mutex;
    pthread_cond_t _new_value_cond;
    pthread_cond_t _verification_cond;
    bool _consumer_started;
    bool _input_is_closed;
    bool _value_is_verified;

    Value _value;
};

void* producer_routine(void* arg) {
    ProcessValueHandlerCookie* cookie = (ProcessValueHandlerCookie*)arg;

    cookie->wait_for_consumer_start();

    std::vector<int> values;
    int next;
    while (std::cin >> next) { //make all IO operations at once
        values.push_back(next);
    }

    for (int i = 0; i < values.size(); i++) {
        int next_value = values[i];
        cookie->start_sync_block();
        while (!cookie -> check_value_verified())
            cookie -> wait_for_verification();

        cookie->update(next_value);
        cookie->set_input_closed(i == values.size() - 1);
        cookie->end_sync_block();
    }

    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    ProcessValueHandlerCookie* cookie = (ProcessValueHandlerCookie*)arg;

    int* sum = new int(0);

    bool input_closed = false;
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    cookie -> notify_consumer_started();

    while (!input_closed) {
        cookie -> start_sync_block();
        while (cookie -> check_value_verified())
            cookie -> wait_for_new_value();

        *sum += cookie->get_value();
        cookie->send_verification();

        input_closed = cookie->is_input_closed();
        cookie -> end_sync_block();
    }
    pthread_exit(sum);
}

void* consumer_interruptor_routine(void* arg) {
    std::pair<pthread_t *, ProcessValueHandlerCookie *> *interruptor_args = (std::pair<pthread_t*, ProcessValueHandlerCookie*>*) arg;
    pthread_t * consumer_thread = interruptor_args->first;
    ProcessValueHandlerCookie* cookie = interruptor_args->second;

    cookie-> wait_for_consumer_start();

    while (!cookie->is_input_closed()) {
        pthread_cancel(*consumer_thread);
    }
    pthread_exit(NULL);
}

int run_threads() {
    pthread_t producer_thread, consumer_thread, interruptor_thread;
    ProcessValueHandlerCookie cookie;

    pthread_create(&consumer_thread, NULL, consumer_routine, &cookie);
    pthread_create(&producer_thread, NULL, producer_routine, &cookie);

    std::pair<pthread_t*, ProcessValueHandlerCookie*> interruptor_args = std::make_pair(&consumer_thread, &cookie);
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, &interruptor_args);

    int* result = NULL;

    pthread_join(consumer_thread, (void **) &result);
    pthread_join(producer_thread, NULL);
    pthread_join(interruptor_thread, NULL);

    int result_value = *result;
    delete result;

    return result_value;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}