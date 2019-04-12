#include <pthread.h>
#include <iostream>
#include <unistd.h>
#include <cstdlib>
#include <stdlib.h>
#include <fstream>
#define NOERROR 0
#define OVERFLOW 1

pthread_mutex_t mutex;
pthread_cond_t produce;
pthread_cond_t consume;

int value = 0;
int max_consumer_sleep = 0;
int consumer_count = 0;
int consumer_exist = 0;
bool producer_finished = false;
bool update_value = false;
static unsigned int timer;
thread_local int error = NOERROR;
//std::ifstream inFile;

struct return_struct {
    int sum;
    int err;
};

int get_last_error() {
    return error;
}

void set_last_error(int code) {
    error = code;
}

bool check_overflow(int a, int b) {
    return (a <= 0 && b <= 0 && a + b > 0) || (a >= 0 && b >= 0 && a + b < 0);
}

void* producer_routine(void* arg) {

    int data = 0;
    while(/*inFile*/std::cin >> data) {

        pthread_mutex_lock(&mutex);

        while(update_value && consumer_exist) {
            pthread_cond_wait(&produce, &mutex);
        }

        if(!consumer_exist) {
            pthread_mutex_unlock(&mutex);
            break;
        }

        value = data;
        update_value = true;

        pthread_cond_signal(&consume);
        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    producer_finished = true;
    pthread_cond_signal(&consume);
    pthread_mutex_unlock(&mutex);
    pthread_exit(nullptr);
}

void* consumer_routine(void* arg) {

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    set_last_error(NOERROR);
    int sum = 0;
    int *value = (int*)arg;

    while(true) {
        pthread_mutex_lock(&mutex);

        while(!update_value && !producer_finished) {
            pthread_cond_wait(&consume, &mutex);
        }

        if(!update_value && producer_finished) {
            consumer_exist--;
            pthread_cond_signal(&consume);
            pthread_mutex_unlock(&mutex);
            break;
        }

        if (check_overflow(sum, *value)) {
            set_last_error(OVERFLOW);
            update_value = false;
            consumer_exist--;
            pthread_cond_signal(&produce);
            pthread_mutex_unlock(&mutex);
            break;
        }

        sum += *value;
        update_value = false;

        pthread_cond_signal(&produce);
        pthread_mutex_unlock(&mutex);
        usleep(rand_r(&timer) % (max_consumer_sleep + 1));
    }
    auto *res = (return_struct*)malloc(sizeof(return_struct));
    *res = {sum,get_last_error()};
    pthread_exit(res);
}

void* consumer_interruptor_routine(void* arg) {

    while(!producer_finished) {
        pthread_cancel(((pthread_t *)(arg))[rand_r(&timer)%consumer_count]);
    }

    pthread_exit(nullptr);
}

int run_threads(int* err) {

    timer = time(0);
    pthread_mutex_init(&mutex, nullptr);
    pthread_cond_init(&produce, nullptr);
    pthread_cond_init(&consume, nullptr);
    pthread_t consumer[consumer_count], producer, interruptor;

    pthread_create(&producer, nullptr, producer_routine, nullptr);
    consumer_exist = consumer_count;
    for(int i = 0; i < consumer_count; i++)
        pthread_create(&consumer[i], nullptr, consumer_routine, &value);
    pthread_create(&interruptor, nullptr, consumer_interruptor_routine, &consumer);

    pthread_join(producer, nullptr);
    int common_sum = 0;
    bool is_error = false;
    return_struct* result = nullptr;
    for(int i = 0; i < consumer_count; i++) {
        pthread_join(consumer[i], (void **)&result);
        if ((*result).err) is_error = true;
        if (!is_error && check_overflow(common_sum, (*result).sum)) is_error = true;
        if (!is_error) common_sum += (*result).sum;
    }
    pthread_join(interruptor, nullptr);

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&produce);
    pthread_cond_destroy(&consume);

    delete result;
    *err = is_error ? OVERFLOW : NOERROR;
    return common_sum;
}

int main(int argc, char *argv[]) {
    consumer_count = atoi(argv[1]);
    max_consumer_sleep = atoi(argv[2]);
    //inFile.open("input.txt");
    int err = 0;
    int ans = run_threads(&err);
    if (err) {
        std::cout << "overflow" << std::endl;
    } else {
        std::cout << ans << std::endl;
    }
    return 0;
}
