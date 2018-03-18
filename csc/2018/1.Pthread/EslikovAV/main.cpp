#include <iostream>

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t cond_consumer_started = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_produced_new_value = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_consumer_finished_processing_value = PTHREAD_COND_INITIALIZER;

bool consumer_started = false;
bool producer_finished = false;
bool has_unprocessed_value = false;

int shared_value;

void wait_for_consumer() {
    pthread_mutex_lock(&mutex);
    while (!consumer_started) {
        pthread_cond_wait(&cond_consumer_started, &mutex);
    }
    pthread_mutex_unlock(&mutex);
}

void *interuptor_routine(void *args) {

    wait_for_consumer();

    auto consumer_thread = static_cast<pthread_t *>(args);

    while (!producer_finished) {
        pthread_cancel(*consumer_thread);
    }

    pthread_exit(EXIT_SUCCESS);
}

void *producer_routine(void *) {

    wait_for_consumer();

    int next_value;



    while (std::cin >> next_value) {
        pthread_mutex_lock(&mutex);
        while (has_unprocessed_value) {
            pthread_cond_wait(&cond_consumer_finished_processing_value, &mutex);
        }
        shared_value = next_value;
        has_unprocessed_value = true;
        pthread_cond_signal(&cond_produced_new_value);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    producer_finished = true;
    has_unprocessed_value = true;
    pthread_cond_signal(&cond_produced_new_value);
    pthread_mutex_unlock(&mutex);

    pthread_exit(EXIT_SUCCESS);
}

void *consumer_routine(void *) {

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    pthread_mutex_lock(&mutex);
    consumer_started = true;
    pthread_cond_broadcast(&cond_consumer_started);
    pthread_mutex_unlock(&mutex);

    auto *sum = new int;

    while (true) {

        pthread_mutex_lock(&mutex);
        while (!has_unprocessed_value) {
            pthread_cond_wait(&cond_produced_new_value, &mutex);
        }
        has_unprocessed_value = false;
        if(producer_finished){
            pthread_cond_signal(&cond_consumer_finished_processing_value);
            pthread_mutex_unlock(&mutex);
            break;
        } else {
            *sum += shared_value;

            pthread_cond_signal(&cond_consumer_finished_processing_value);
            pthread_mutex_unlock(&mutex);
        }
    }

    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, nullptr);
    pthread_exit((void *) sum);
}

int run_threads() {
    int *buf;

    pthread_t producer_thread;
    pthread_t consumer_thread;
    pthread_t interruptor_thread;

    pthread_create(&consumer_thread, nullptr, consumer_routine, nullptr);
    pthread_create(&interruptor_thread, nullptr, interuptor_routine, (void *) &consumer_thread);
    pthread_create(&producer_thread, nullptr, producer_routine, nullptr);

    pthread_join(producer_thread, nullptr);
    pthread_join(consumer_thread, (void **) &buf);
    pthread_join(interruptor_thread, nullptr);

    int result = *buf;
    delete buf;

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}