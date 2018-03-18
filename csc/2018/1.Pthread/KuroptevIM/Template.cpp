#include <pthread.h>
#include <iostream>


pthread_t producer;
pthread_t consumer;
pthread_t interruptor;

int value = 0;

pthread_mutex_t mutex_update = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t mutex_start = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t producer_worked = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_begin = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_worked = PTHREAD_COND_INITIALIZER;
pthread_cond_t interrupt_begin = PTHREAD_COND_INITIALIZER;

bool consumer_ready = true;
bool producer_done = false;
bool consumer_start = false;
bool interrupt_start = false;

void *producer_routine(void *arg)
{
    pthread_mutex_lock(&mutex_start);
    while (!interrupt_start)
        pthread_cond_wait(&interrupt_begin, &mutex_start);
    pthread_mutex_unlock(&mutex_start);
    
    int val = 0;
    while (std::cin >> val)
    {
        pthread_mutex_lock(&mutex_update);
        while (!consumer_ready)
            pthread_cond_wait(&consumer_worked, &mutex_update);
        consumer_ready = false;
        value = val;
        pthread_cond_signal(&producer_worked);
        pthread_mutex_unlock(&mutex_update);
    }
    pthread_mutex_lock(&mutex_update);
    while (!consumer_ready)
        pthread_cond_wait(&consumer_worked, &mutex_update);
    consumer_ready = false;
    producer_done = true;
    pthread_cond_signal(&producer_worked);
    pthread_mutex_unlock(&mutex_update);
    pthread_exit(NULL);
}

void *consumer_routine(void *arg)
{
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, 0);
    
    pthread_mutex_lock(&mutex_start);
    consumer_start = true;
    pthread_cond_signal(&consumer_begin);
    pthread_mutex_unlock(&mutex_start);
    
    int* sum = new int;
    while (true)
    {
        pthread_mutex_lock(&mutex_update);
        while (consumer_ready)
            pthread_cond_wait(&producer_worked, &mutex_update);
        if (producer_done)
        {
            pthread_mutex_unlock(&mutex_update);
            break;
        }
        *sum += value;
        consumer_ready = true;
        pthread_cond_signal(&consumer_worked);
        pthread_mutex_unlock(&mutex_update);
    }
    return sum;
}

void *interruptor_routine(void *arg) {
    pthread_mutex_lock(&mutex_start);
    while (!consumer_start)
        pthread_cond_wait(&consumer_begin, &mutex_start);
    interrupt_start = true;
    pthread_cond_signal(&interrupt_begin);
    pthread_mutex_unlock(&mutex_start);
    
    pthread_t* try_thread = (pthread_t*)arg;
    while (!pthread_cancel(*try_thread));
    pthread_exit(NULL);
}

int run_threads()
{
    pthread_create(&producer, NULL, producer_routine, NULL);
    pthread_create(&consumer, NULL, consumer_routine, NULL);
    pthread_create(&interruptor, NULL, interruptor_routine, &consumer);
 
    int *res;
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **)&res);
    pthread_join(interruptor, NULL);
    
    int sum = *res;
    delete res;
    return sum;
}

int main()
{
    std::cout << run_threads() << std::endl;
    return 0;
}
