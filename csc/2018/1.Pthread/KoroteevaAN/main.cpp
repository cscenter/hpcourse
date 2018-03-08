#include <pthread.h>
#include <iostream>
#include <vector>

pthread_t producer;
pthread_t consumer;
pthread_t consumer_interruptor;

pthread_mutex_t mutex;
pthread_mutex_t work_done_mutex;

pthread_cond_t cond_wait_consumer_start;
pthread_cond_t cond_can_consume;
pthread_cond_t cond_can_produce;

bool consumer_starded = false;
bool work_done = false;

int data = 0;
bool is_data_updated = false;

void wait_for_consumer_to_start() {
    pthread_mutex_lock(&mutex);
    if (!consumer_starded) pthread_cond_wait(&cond_wait_consumer_start, &mutex);
    pthread_mutex_unlock(&mutex);
}

bool is_work_done() {
    pthread_mutex_lock(&work_done_mutex);
    if (work_done) {
        pthread_mutex_unlock(&work_done_mutex);
        return true;
    }
    pthread_mutex_unlock(&work_done_mutex);
    return false;

}

void* producer_routine(void* arg) {
    wait_for_consumer_to_start();
    std::cout << "producer started" << std::endl;

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    std::cout << "please enter number of numbers to be proceed" << std::endl;
    int num;
    std::cin >> num;
    std::cout << "please enter " << num << " numbers" << std::endl;

    for (int i = 0; i < num; i++) {
        int new_data;
        std::cin >> new_data;
        pthread_mutex_lock(&mutex);
        if (is_data_updated) pthread_cond_wait(&cond_can_produce, &mutex);
        data = new_data;
        is_data_updated = true;
        std::cout << "Produced data: " << data << std::endl;
        pthread_cond_signal(&cond_can_consume);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&work_done_mutex);
    work_done = true;
    pthread_cond_signal(&cond_can_consume);
    pthread_mutex_unlock(&work_done_mutex);

    pthread_exit(nullptr);
}

void* consumer_routine(void* arg) {
    // notify about start
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    std::cout << "consumer started" << std::endl;
    pthread_mutex_lock(&mutex);
    consumer_starded = true;
    pthread_cond_broadcast(&cond_wait_consumer_start);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    auto * result_sum = new int(0);

    // for every update issued by producer, read the value and add to sum
    while(!is_work_done()) {
        pthread_mutex_lock(&mutex);

        if (!is_data_updated) pthread_cond_wait(&cond_can_consume, &mutex);

        if (is_data_updated) {
            is_data_updated = false;
            *result_sum += data;
            std::cout << "Consumed data: " << data << std::endl;
            pthread_cond_signal(&cond_can_produce);
        }
        pthread_mutex_unlock(&mutex);
    }

    // return pointer to result
    pthread_exit(result_sum);
}

void* consumer_interruptor_routine(void* arg) {
    wait_for_consumer_to_start();
    std::cout << "interruptor started" << std::endl;

    // interrupt consumer while producer is running
    while (!is_work_done()) pthread_cancel(consumer);

    pthread_exit(nullptr);
}

int run_threads() {
    // start 3 threads and wait until they're done

    pthread_cond_init(&cond_wait_consumer_start, nullptr);
    pthread_cond_init(&cond_can_consume, nullptr);
    pthread_cond_init(&cond_can_produce, nullptr);

    pthread_mutex_init(&mutex, nullptr);
    pthread_mutex_init(&work_done_mutex, nullptr);

    pthread_create(&producer, nullptr, producer_routine, nullptr);
    pthread_create(&consumer, nullptr, consumer_routine, nullptr);
    pthread_create(&consumer_interruptor, nullptr, consumer_interruptor_routine, nullptr);

    int* heap_res;
    pthread_join(producer, nullptr);
    pthread_join(consumer, reinterpret_cast<void **>(&heap_res));
    pthread_join(consumer_interruptor, nullptr);

    int res = *heap_res;
    delete(heap_res);

    // return sum of update values seen by consumer
    return res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
