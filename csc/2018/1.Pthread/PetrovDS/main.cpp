#include <iostream>
#include <queue>
#include <pthread.h>
#include <unistd.h>

using namespace std;

struct shared_mem {
    int data;
    bool end_of_sequence;
};

bool consumer_ready;
bool producer_ready;
bool consumer_state;

pthread_mutex_t mut; // mutex for struct shared_mem
pthread_mutex_t mut_consumer_ready;
pthread_mutex_t mut_producer_ready;
pthread_mutex_t mut_consumer_state;

pthread_cond_t cond_consumer;
pthread_cond_t cond_producer;
pthread_cond_t cond_consumer_state;

void notify_consumer_state() {
    pthread_mutex_lock(&mut_consumer_state);
    consumer_state = true;
    pthread_cond_broadcast(&cond_consumer_state);
    pthread_mutex_unlock(&mut_consumer_state);
}

void notify_consumer() {
    pthread_mutex_lock(&mut_consumer_ready);
    consumer_ready = true;
    pthread_cond_broadcast(&cond_consumer);
    pthread_mutex_unlock(&mut_consumer_ready);
}

void notify_producer() {
    pthread_mutex_lock(&mut_producer_ready);
    producer_ready = true;
    pthread_cond_broadcast(&cond_producer);
    pthread_mutex_unlock(&mut_producer_ready);
}

void wait_for_consumer_state() {
    pthread_mutex_lock(&mut_consumer_state);
    while (!consumer_state)
        pthread_cond_wait(&cond_consumer_state, &mut_consumer_state);
    consumer_state = false;
    pthread_mutex_unlock(&mut_consumer_state);
}

void wait_for_producer() {
    pthread_mutex_lock(&mut_producer_ready);
    while (!producer_ready)
        pthread_cond_wait(&cond_producer, &mut_producer_ready);
    producer_ready = false;
    pthread_mutex_unlock(&mut_producer_ready);
}

void wait_for_consumer() {
    pthread_mutex_lock(&mut_consumer_ready);
    while (!consumer_ready)
        pthread_cond_wait(&cond_consumer, &mut_consumer_ready);
    consumer_ready = false;
    pthread_mutex_unlock(&mut_consumer_ready);
}

void* producer_routine(void* arg) {
    shared_mem* mem = (shared_mem*) arg;

    int next;
    queue<int> q_input;

    while (true) {
        cin >> next;
        if (!cin)
            break;
        q_input.push(next);
    }

    int count = q_input.size();
    for (int i = 0; i < count; ++i) {
        wait_for_consumer();

        // new data
        pthread_mutex_lock(&mut);

        mem->data = q_input.front();
        q_input.pop();

        pthread_mutex_unlock(&mut);

        notify_producer();
    }

    wait_for_consumer();
    pthread_mutex_lock(&mut);
    mem->end_of_sequence = true;
    pthread_mutex_unlock(&mut);

    notify_producer();

    //
    pthread_exit(nullptr);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    notify_consumer();
    notify_consumer_state();

    shared_mem* mem = (shared_mem*) arg;
    int *sum = new int();

    while (true) {
        wait_for_producer();

        // read data:
        pthread_mutex_lock(&mut);
        if (mem->end_of_sequence) {
            pthread_mutex_unlock(&mut);
            break;
        }
        *sum += mem->data;
        pthread_mutex_unlock(&mut);

        notify_consumer();
    }

    notify_consumer_state();
    pthread_exit(sum);
}

void* interruptor_routine(void* arg) {
    wait_for_consumer_state();

    while (true) {
        pthread_cancel(*((pthread_t*) arg));

        pthread_mutex_lock(&mut_consumer_state);
        if (consumer_state) {
            pthread_mutex_unlock(&mut_consumer_state);
            break;
        }
        pthread_mutex_unlock(&mut_consumer_state);
    }

    return nullptr;
}

int run_threads() {
    shared_mem data;
    data.end_of_sequence = false;

    consumer_ready = false;
    producer_ready = false;
    consumer_state = false;

    pthread_mutex_init(&mut, nullptr);
    pthread_mutex_init(&mut_consumer_ready, nullptr);
    pthread_mutex_init(&mut_producer_ready, nullptr);
    pthread_mutex_init(&mut_consumer_state, nullptr);

    pthread_t thr_producer;
    if (0 != pthread_create(&thr_producer, nullptr, producer_routine, (void*)(&data)))
        throw std::runtime_error("pthread_create() failed");

    pthread_t thr_consumer;
    if (0 != pthread_create(&thr_consumer, nullptr, consumer_routine, (void*)(&data)))
        throw std::runtime_error("pthread_create() failed");
    
    pthread_t thr_intrerupator;
    if (0 != pthread_create(&thr_intrerupator, nullptr, interruptor_routine, (void*)(&thr_consumer)))
        throw std::runtime_error("pthread_create() failed");

    int *p_sum;
    pthread_join(thr_consumer, (void**)&p_sum);
    pthread_join(thr_producer, nullptr);
    pthread_join(thr_intrerupator, nullptr);
    
    
    return *p_sum;
}

int main() {
    cout << run_threads() << '\n';
    return 0;
}

