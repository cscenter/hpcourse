#include <iostream>
#include <pthread.h>
#include <sstream>
#include <vector>
#include <unistd.h>

#define NOERROR 0
#define OVERFLOW 1

thread_local int error = NOERROR;

int get_last_error() {
    return error;
}

void set_last_error(int error_code) {
    error = error_code;
}

class return_value {
public:
    return_value() : value_(0) {}

    void set(int value) {
        value_ = value;
    }

    int value() const {
        return value_;
    }

private:
    int value_;
};

struct thread_data {
    unsigned thread_id;
    unsigned max_sleep_time;
    return_value *value;
    long *sum;

};

pthread_mutex_t consumer_start_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t value_write_cond = PTHREAD_COND_INITIALIZER;
pthread_cond_t value_read_cond = PTHREAD_COND_INITIALIZER;
pthread_mutex_t value_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumer_start_condition = PTHREAD_COND_INITIALIZER;
bool is_data_over = false;
bool is_consumer_started = false;
bool is_data_ready = false;

void *producer_routine(void *arg) {
    auto *value = (return_value *)arg;

    pthread_mutex_lock(&consumer_start_mutex);
    while (!is_consumer_started) {
        pthread_cond_wait(&consumer_start_condition, &consumer_start_mutex);
    }
    pthread_mutex_unlock(&consumer_start_mutex);

    std::string line;
    std::getline(std::cin, line);

    std::stringstream iss(line);

    std::vector<int> numbers;
    int number;
    while (iss >> number) {
        numbers.push_back(number);
    }

    pthread_mutex_lock(&value_mutex);

    for (int i : numbers) {
        value->set(i);
        is_data_ready = true;

        pthread_cond_signal(&value_write_cond);

        while (is_data_ready) {
            pthread_cond_wait(&value_read_cond, &value_mutex);
        }
    }

    is_data_over = true;
    pthread_cond_broadcast(&value_write_cond);

    pthread_mutex_unlock(&value_mutex);

    pthread_exit(nullptr);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    auto *data = (thread_data *)arg;

    int cur_sum = 0;

    pthread_mutex_lock(&consumer_start_mutex);
    if (!is_consumer_started) {
        is_consumer_started = true;
        pthread_cond_broadcast(&consumer_start_condition);
    }
    pthread_mutex_unlock(&consumer_start_mutex);


    while (true) {
        pthread_mutex_lock(&value_mutex);

        while (!is_data_ready && !is_data_over) {
            pthread_cond_wait(&value_write_cond, &value_mutex);
        }

        if (is_data_over) {
            pthread_mutex_unlock(&value_mutex);
            pthread_exit(data->sum);
        }

        if (cur_sum > INT32_MAX - data->value->value() && cur_sum < INT32_MIN - data->value->value()) {
            set_last_error(OVERFLOW);
            __gthrw_pthread_cancel(data->thread_id);
            break;
        }

        *(data->sum) += data->value->value();
        is_data_ready = false;

        pthread_cond_signal(&value_read_cond);

        pthread_mutex_unlock(&value_mutex);

        auto sleep_time = data->max_sleep_time * 1000 * rand_r(&(data->thread_id)) / RAND_MAX;
        usleep((__useconds_t)sleep_time);
    }
}

void *consumer_interruptor_routine(void *arg) {
    auto *consumers = (std::vector<pthread_t> *) arg;

    pthread_mutex_lock(&consumer_start_mutex);
    while (!is_consumer_started) {
        pthread_cond_wait(&consumer_start_condition, &consumer_start_mutex);
    }
    pthread_mutex_unlock(&consumer_start_mutex);

    while (!is_data_over) {
        pthread_cancel(consumers->at(rand() % consumers->size()));
    }

    pthread_exit(nullptr);
}

int run_threads(int n_consumers, int max_consumer_sleep_time) {
    srand((unsigned)time(nullptr));

    auto *value = new return_value();
    auto *sum = new long(0);

    pthread_t producer;
    pthread_create(&producer, nullptr, producer_routine, value);

    std::vector<pthread_t> consumers;
    for (unsigned i = 0; i < n_consumers; i++) {
        auto *data = new thread_data();
        data->value = value;
        data->max_sleep_time = (unsigned)max_consumer_sleep_time;
        data->sum = sum;
        data->thread_id = i;

        pthread_t consumer;
        pthread_create(&consumer, nullptr, consumer_routine, (void *)data);
        consumers.push_back(consumer);
    }

    pthread_t consumer_interruptor;
    pthread_create(&consumer_interruptor, nullptr, consumer_interruptor_routine, &consumers);

    void *result = nullptr;

    pthread_join(producer, nullptr);
    pthread_join(consumer_interruptor, nullptr);
    pthread_join(consumers[0], &result);
    for (int i = 1; i < n_consumers; i++) {
        pthread_join(consumers[i], nullptr);
    }


    return *(int *)result;
}

int main(int argc, char *argv[]) {
    if (argc != 3)
        return 1;

    int n_consumers = atoi(argv[1]);
    int max_time_to_sleep = atoi(argv[2]);

    return run_threads(n_consumers, max_time_to_sleep);
}

