#include <pthread.h>
#include <iostream>
#include <random>
#include <unistd.h>
#include <limits.h>

#define NOERROR 0
#define OVERFLOW_ERROR 1

int consumers_count = 0;
int sleep_limit = 0;
int data = 0;

thread_local int error = NOERROR;

pthread_mutex_t consumer_running_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumer_running_cond = PTHREAD_COND_INITIALIZER;
int cur_consumer_count = 0;

pthread_mutex_t producer_to_consumer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t producer_to_consumer_cond = PTHREAD_COND_INITIALIZER;
bool got_data = false;
bool end_reading_data = false;

pthread_mutex_t consumer_to_producer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumer_to_producer_cond = PTHREAD_COND_INITIALIZER;
bool processed_data = false;

int get_last_error() {
    // return per-thread error code
    return error;
}


void set_last_error(int code) {
    // set per-thread error code
    error = code;
}

struct Result {
    int sum;
    int error;
};

bool check_overflow(int sum, int value) {
    return (value > 0 && sum > INT_MAX - value) || (value < 0 && sum < INT_MIN - value);
}

void wait_consumer_start() {
    pthread_mutex_lock(&consumer_running_mutex);
    if (cur_consumer_count <= 0) {
        pthread_cond_wait(&consumer_running_cond, &consumer_running_mutex);
    }
    pthread_mutex_unlock(&consumer_running_mutex);
}

void* producer_routine(void* arg) {
    // wait for consumer to start
    wait_consumer_start();

    // read data, loop through each value and update the value, notify consumer, wait for consumer to process
    while (std::cin >> data) {
        pthread_mutex_lock(&producer_to_consumer_mutex);
        got_data = true;
        pthread_cond_signal(&producer_to_consumer_cond);
        pthread_mutex_unlock(&producer_to_consumer_mutex);

        pthread_mutex_lock(&consumer_to_producer_mutex);
        while (!processed_data) {
            pthread_cond_wait(&consumer_to_producer_cond, &consumer_to_producer_mutex);
        }
        processed_data = false;
        pthread_mutex_unlock(&consumer_to_producer_mutex);
    }
    // notify all consumers about ending of reading
    pthread_mutex_lock(&producer_to_consumer_mutex);
    end_reading_data = true;
    pthread_cond_broadcast(&producer_to_consumer_cond);
    pthread_mutex_unlock(&producer_to_consumer_mutex);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    // notify about start
    pthread_mutex_lock(&consumer_running_mutex);
    cur_consumer_count++;
    pthread_cond_signal(&consumer_running_cond);
    pthread_mutex_unlock(&consumer_running_mutex);

    // for every update issued by producer, read the value and add to sum
    int local_sum = 0;

    while (true) {
        pthread_mutex_lock(&producer_to_consumer_mutex);
        while (!got_data && !end_reading_data) {
            pthread_cond_wait(&producer_to_consumer_cond, &producer_to_consumer_mutex);
        }

        if (got_data) {
            int data = *(int *)arg;
            got_data = false;
            pthread_mutex_unlock(&producer_to_consumer_mutex);

            pthread_mutex_lock(&consumer_to_producer_mutex);
            processed_data = true;
            pthread_cond_signal(&consumer_to_producer_cond);
            pthread_mutex_unlock(&consumer_to_producer_mutex);

            if (check_overflow(local_sum, data)) {
                set_last_error(OVERFLOW_ERROR);
                break;
            }

            local_sum += data;

            usleep(rand() % (sleep_limit + 1) * 1000);
        }

        if (end_reading_data) {
            pthread_mutex_unlock(&producer_to_consumer_mutex);
            break;
        }
    }

    pthread_mutex_lock(&consumer_running_mutex);
    cur_consumer_count--;
    pthread_mutex_unlock(&consumer_running_mutex);

    // return pointer to result (for particular consumer)
    Result *res = new Result;
    res->sum = local_sum;
    res->error = get_last_error();
    return (void *) res;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumers to start
    wait_consumer_start();

    // interrupt random consumer while producer is running
    pthread_t *consumers = (pthread_t *)arg;
    while (!end_reading_data) {
        int n = rand() % cur_consumer_count;
        pthread_cancel(consumers[n]);
    }
}

int run_threads() {
    int sum = 0;

    pthread_t producer, interruptor;

    pthread_t consumers[consumers_count];

    pthread_create(&producer, nullptr, producer_routine, nullptr);
    // start N threads and wait until they're done
    // return aggregated sum of values
    for (int i = 0; i < consumers_count; ++i) {
        pthread_create(&consumers[i], nullptr, consumer_routine, &data);
    }

    pthread_create(&interruptor, nullptr, consumer_interruptor_routine, &consumers);

    pthread_join(producer, nullptr);
    pthread_join(interruptor, nullptr);
    for (int i = 0; i < consumers_count; ++i) {
        Result* result;
        pthread_join(consumers[i], (void **) &result);
        if (result->error == OVERFLOW_ERROR || check_overflow(sum, result->sum))
            set_last_error(OVERFLOW_ERROR);
        if (get_last_error() != OVERFLOW_ERROR)
            sum += result->sum;
        delete(result);
    }
    if (get_last_error() == OVERFLOW_ERROR) {
        std::cout << "overflow" << std::endl;
        return 1;
    }
    std::cout << sum << std::endl;
    return 0;
}

int main(int argc, char* argv[]) {
    consumers_count = atoi(argv[1]);
    sleep_limit = atoi(argv[2]);
    return run_threads();
}
