#include <iostream>
#include <unistd.h>

#define NOERROR 0
#define OVERFLOW 1

static const char *const overflow_message = "overflow";

int total_number_of_consumers;
int consumer_max_sleep_time;
int number_of_started_consumers;
int shared_value;

bool producer_finished = false;
bool has_unprocessed_value = false;

pthread_mutex_t value_processing_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumers_start_mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t cond_consumers_started = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_consumer_finished_processing_value = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_produced_new_value = PTHREAD_COND_INITIALIZER;

thread_local int error_code = NOERROR;

struct Result {
    const int sum;
    const int error_code;
};

int random_number(int from, int to) {
    if (to == 0) {
        return 0;
    }
    return from + std::rand() % to;
}

int add_with_overflow_check(int *result, int a, int b) {
    if (a > INT_MAX - b) {
        return -1;
    } else {
        *result = a + b;
        return 0;
    }
}

void wait_for_consumer() {

    pthread_mutex_lock(&consumers_start_mutex);
    while (number_of_started_consumers < total_number_of_consumers) {
        pthread_cond_wait(&cond_consumers_started, &consumers_start_mutex);
    }

    pthread_mutex_unlock(&consumers_start_mutex);
}

int get_last_error() {
    return error_code;
}

void set_last_error(int code) {
    error_code = code;
}

void *producer_routine(void *) {
    wait_for_consumer();

    int next_value;

    while (std::cin >> next_value) {
        pthread_mutex_lock(&value_processing_mutex);
        while (has_unprocessed_value) {
            pthread_cond_wait(&cond_consumer_finished_processing_value, &value_processing_mutex);
        }
        shared_value = next_value;
        has_unprocessed_value = true;
        pthread_cond_signal(&cond_produced_new_value);
        pthread_mutex_unlock(&value_processing_mutex);
    }

    pthread_mutex_lock(&value_processing_mutex);
    while (has_unprocessed_value) {
        pthread_cond_wait(&cond_consumer_finished_processing_value, &value_processing_mutex);
    }
    next_value = 0;
    producer_finished = true;
    has_unprocessed_value = true;
    pthread_cond_signal(&cond_produced_new_value);
    pthread_mutex_unlock(&value_processing_mutex);

    pthread_exit(EXIT_SUCCESS);
}


void *consumer_routine(void *) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    pthread_mutex_lock(&consumers_start_mutex);
    number_of_started_consumers++;
    pthread_cond_broadcast(&cond_consumers_started);
    pthread_mutex_unlock(&consumers_start_mutex);

    int sum = 0;

    while (true) {
        pthread_mutex_lock(&value_processing_mutex);
        while (!has_unprocessed_value) {
            pthread_cond_wait(&cond_produced_new_value, &value_processing_mutex);
        }
        has_unprocessed_value = false;

        if (producer_finished) {
            pthread_cond_signal(&cond_consumer_finished_processing_value);
            pthread_mutex_unlock(&value_processing_mutex);
            break;
        }

        if (add_with_overflow_check(&sum, sum, shared_value)) {
            set_last_error(OVERFLOW);
            pthread_cond_signal(&cond_consumer_finished_processing_value);
            pthread_mutex_unlock(&value_processing_mutex);
            break;
        }

        pthread_cond_signal(&cond_consumer_finished_processing_value);
        pthread_mutex_unlock(&value_processing_mutex);

        int rand_sleep_time = random_number(0, consumer_max_sleep_time);

        usleep(rand_sleep_time * 1000);
    }

    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, nullptr);
    auto result = new Result{sum = sum, error_code = get_last_error()};
    pthread_exit(result);
}

void *consumer_interruptor_routine(void *arg) {
    wait_for_consumer();

    auto consumers = static_cast<pthread_t *>(arg);

    while (!producer_finished) {
        int random_consumer_index = random_number(0, total_number_of_consumers);
        pthread_cancel(consumers[random_consumer_index]);
    }

    pthread_exit(EXIT_SUCCESS);
}


int run_threads() {
    int sum = 0;

    pthread_t producer_thread;
    pthread_t interruptor_thread;
    pthread_t consumers[total_number_of_consumers];

    for (int i = 0; i < total_number_of_consumers; i++) {
        pthread_create(&consumers[i], nullptr, consumer_routine, nullptr);
    }
    pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, &consumers);
    pthread_create(&producer_thread, nullptr, producer_routine, nullptr);

    pthread_join(producer_thread, nullptr);
    pthread_join(interruptor_thread, nullptr);

    for (int i = 0; i < total_number_of_consumers; ++i) {
        Result *result;
        pthread_join(consumers[i], (void **) &result);
        if (result->error_code == OVERFLOW) {
            std::cout << overflow_message;
            delete result;
            return 1;
        }
        if (add_with_overflow_check(&sum, sum, result->sum)) {
            std::cout << overflow_message;
            delete result;
            return 1;
        }
        delete result;
    }

    std::cout << sum << std::endl;
    return EXIT_SUCCESS;
}

int main(int argc, char *argv[]) {
    if (argc != 3) {
        return EXIT_FAILURE;
    }

    total_number_of_consumers = atoi(argv[1]);
    consumer_max_sleep_time = atoi(argv[2]);

    return run_threads();
}