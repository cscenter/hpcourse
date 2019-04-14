#include <cstdlib>
#include <unistd.h>
#include <pthread.h>
#include <iostream>
#include <random>

using namespace std;

#define NOERROR 0
#define OVERFLOW 1

int consumers_count;
int max_sleep_time;

int storage = 0;
bool storage_updated = false;
bool producer_done = false;

// producer/consumers sync
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t produced = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumed = PTHREAD_COND_INITIALIZER;

// consumers/interruptor start sync
int started_consumers = 0;
pthread_mutex_t on_start_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumers_started = PTHREAD_COND_INITIALIZER;

thread_local int error = NOERROR;

int get_last_error() {
    return error;
}

void set_last_error(int code) {
    error = code;
}

// true if overflow occurred, false otherwise
bool checked_add(int fst, int snd, int* r_sum) {
    *r_sum = fst + snd;
    return (fst > 0 && snd > 0 && *r_sum <= 0)
            || (fst < 0 && snd < 0 && *r_sum >= 0);
}

struct Result {
    int partial_sum;
    int error;
};

Result* consumer_result(int sum) {
    auto result = (Result*)(malloc(sizeof(Result)));
    result->partial_sum = sum;
    result->error = get_last_error();
    return result;
}

void* producer_routine(void* arg) {
    int* storage_arg = static_cast<int*>(arg);

    int n;
    while (cin >> n) {
        pthread_mutex_lock(&mutex);

        while (storage_updated) {
            pthread_cond_wait(&consumed, &mutex);
        }

        *storage_arg = n;
        storage_updated = true;
        pthread_cond_signal(&produced);

        pthread_mutex_unlock(&mutex);
    }

    // we need to wait until the last produced value is consumed
    // then wake up all the waiting consumers and let them finished
    pthread_mutex_lock(&mutex);

    while (storage_updated) {
        pthread_cond_wait(&consumed, &mutex);
    }
    producer_done = true;
    storage_updated = true;
    pthread_cond_broadcast(&produced);

    pthread_mutex_unlock(&mutex);
}

void* consumer_routine(void* arg) {
    // leaving no chance for interruptor
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    // update count and notify interruptor
    pthread_mutex_lock(&on_start_mutex);
    started_consumers++;
    pthread_cond_signal(&consumers_started);
    pthread_mutex_unlock(&on_start_mutex);

    int* storage_arg = static_cast<int*>(arg);
    int sum = 0;

    while (true) {
        pthread_mutex_lock(&mutex);

        while (!storage_updated) {
            pthread_cond_wait(&produced, &mutex);
        }

        if (producer_done) {
            pthread_mutex_unlock(&mutex);
            pthread_exit(consumer_result(sum));
        }

        int current_sum;
        if (checked_add(sum, *storage_arg, &current_sum)) {
            set_last_error(OVERFLOW);

            storage_updated = false;
            pthread_cond_signal(&consumed);

            pthread_mutex_unlock(&mutex);
            pthread_exit(consumer_result(sum));
        }
        sum = current_sum;

        storage_updated = false;
        pthread_cond_signal(&consumed);

        pthread_mutex_unlock(&mutex);

        usleep(rand() % (max_sleep_time + 1) * 1000);
    }
}

void* consumer_interruptor_routine(void* arg) {
    pthread_mutex_lock(&on_start_mutex);

    // wait until all consumers are started
    while (started_consumers < consumers_count) {
        pthread_cond_wait(&consumers_started, &on_start_mutex);
    }

    pthread_mutex_unlock(&on_start_mutex);

    auto consumers = static_cast<pthread_t*>(arg);
    while (!producer_done) {
        pthread_cancel(consumers[rand() % consumers_count]);
    }
}

int run_threads() {
    int sum = 0;

    pthread_t producer;
    pthread_create(&producer, nullptr, producer_routine, &storage);

    pthread_t consumers[consumers_count];
    for (int i = 0; i < consumers_count; i++) {
        pthread_create(&consumers[i], nullptr, consumer_routine, &storage);
    }

    pthread_t interruptor;
    pthread_create(&interruptor, nullptr, consumer_interruptor_routine, consumers);

    // wait until all threads finish the work
    // if one of the consumers returns OVERFLOW we don't wait for others
    pthread_join(producer, nullptr);
    pthread_join(interruptor, nullptr);
    for (int i = 0; i < consumers_count; i++) {
        Result* result;
        pthread_join(consumers[i], (void**) &result);
        if (result->error == OVERFLOW || checked_add(sum, result->partial_sum, &sum)) {
            delete result;
            cout << "overflow";
            return 1;
        }
        delete result;
    }

    cout << sum << endl;
    return 0;
}

int main(int argc, char** argv) {
    consumers_count = atoi(argv[1]);
    max_sleep_time = atoi(argv[2]);

    return run_threads();
}