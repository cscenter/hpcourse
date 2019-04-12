#include <pthread.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <random>

#define NOERROR 0
#define OVERFLOW 1

int n_consumers;
int max_sleep_time;

int current_value;

bool is_value_new = false;
bool produce_finished = false;

pthread_mutex_t the_mutex, start_mutex;
pthread_cond_t condc, condp, consumer_started;

int n_active_consumers = 0;

struct output {
    int local_sum;
    int status_code;
};

thread_local output consumer_output;

int check_overflow(int a, int b) {
    int r = (uint)a + (uint)b;
    return (a < 0 && b < 0 && r >= 0) || (a >= 0 && b >= 0 && r < 0);
}

int get_last_error() {
    return consumer_output.status_code;
}

void set_last_error(int code) {
    consumer_output.status_code = code;
}

void *producer_routine(void *arg) {
    pthread_mutex_lock(&start_mutex);
    if (!n_active_consumers) {
        pthread_cond_wait(&consumer_started, &start_mutex);
    }
    pthread_mutex_unlock(&start_mutex);

    int n;
    while (std::cin >> n) {
        pthread_mutex_lock(&the_mutex);
        while (is_value_new) {
            if (!n_active_consumers) {
                produce_finished = true;
                pthread_cond_broadcast(&condc);
                pthread_exit(0);
            }
            pthread_cond_wait(&condp, &the_mutex);
        }
        current_value = n;
        is_value_new = true;
        pthread_cond_signal(&condc);
        pthread_mutex_unlock(&the_mutex);
    }
    produce_finished = true;
    pthread_cond_broadcast(&condc);
    pthread_exit(0);
}

void *consumer_routine(void *arg) {
    pthread_mutex_lock(&start_mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    n_active_consumers++;
    consumer_output.local_sum = 0;
    consumer_output.status_code = NOERROR;
    pthread_cond_signal(&consumer_started);
    pthread_mutex_unlock(&start_mutex);

    while (true) {
        pthread_mutex_lock(&the_mutex);
        while (!is_value_new) {
            if (produce_finished) {
                n_active_consumers--;

                if (n_active_consumers == 0) {
                    pthread_cond_signal(&condp);
                }
                else pthread_cond_signal(&condc);

                pthread_mutex_unlock(&the_mutex);
                pthread_exit(&consumer_output);
            }
            pthread_cond_wait(&condc, &the_mutex);
        }

        if (check_overflow(consumer_output.local_sum, current_value)) {
            set_last_error(OVERFLOW);
            n_active_consumers--;

            if (n_active_consumers == 0) {
                pthread_cond_signal(&condp);
            }
            else pthread_cond_signal(&condc);

            pthread_mutex_unlock(&the_mutex);
            pthread_exit(&consumer_output);
        }

        consumer_output.local_sum += current_value;
        is_value_new = false;
        pthread_cond_signal(&condp);
        pthread_mutex_unlock(&the_mutex);
        std::this_thread::sleep_for(std::chrono::milliseconds(rand() % (max_sleep_time + 1)));
    }
}

void *consumer_interruptor_routine(void *arg) {
    pthread_mutex_lock(&start_mutex);

    if (!n_active_consumers) {
        pthread_cond_wait(&consumer_started, &start_mutex);
    }
    pthread_mutex_unlock(&start_mutex);

    pthread_t *threads = (pthread_t *)arg;
    while (!produce_finished && n_active_consumers) {
        int n = rand() % n_active_consumers;
        pthread_cancel(threads[2 + n]);
    }
    pthread_exit(0);
}

int run_threads() {
    int sum = 0;
    pthread_mutex_init(&the_mutex, NULL);
    pthread_cond_init(&condc, NULL);
    pthread_cond_init(&condp, NULL);
    pthread_t threads[n_consumers + 2];

    pthread_create(&threads[0], NULL, producer_routine, &current_value);
    for (int i = 2; i < n_consumers + 2; i++) {
        pthread_create(&threads[i], NULL, consumer_routine, &current_value);
    }
    pthread_create(&threads[1], NULL, consumer_interruptor_routine, threads);

    pthread_join(threads[0], NULL);
    for (int i = 2; i < n_consumers + 2; i++) {
        output *out;
        pthread_join(threads[i], (void **)&out);
        if (
        out->status_code == OVERFLOW || check_overflow(sum, out->local_sum)) {
            std::cout << "overflow" << std::endl;
            return 1;
        }
        sum += out->local_sum;
    }
    pthread_join(threads[1], NULL);

    pthread_cond_destroy(&condp);
    pthread_cond_destroy(&condc);
    pthread_mutex_destroy(&the_mutex);
    std::cout << sum << std::endl;
    return 0;
}

int main(int argc, char *argv[]) {
    n_consumers = atoi(argv[1]);
    max_sleep_time = atoi(argv[2]);
    return run_threads();
}