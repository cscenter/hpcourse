#include <pthread.h>  
#include <iostream>
#include <unistd.h>

int data = 0;
bool consumer_started = false;
bool producer_ended = false;
bool data_updated = false;

// `fast mutex`. Will it be in a deadlock??
pthread_mutex_t cs_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond_cs_mutex = PTHREAD_COND_INITIALIZER;
pthread_cond_t cond_intr_mutex = PTHREAD_COND_INITIALIZER;

pthread_cond_t upd_required_mtx = PTHREAD_COND_INITIALIZER;
pthread_mutex_t data_mutex = PTHREAD_MUTEX_INITIALIZER;

void lock_data()
{
    pthread_mutex_lock(&data_mutex);
}

void unlock_data()
{
    pthread_mutex_unlock(&data_mutex);
}

void* producer_routine(void* arg) 
{
    // std::cout << "P: Wait for consumer...\n";
    pthread_mutex_lock(&cs_mutex);
    while (!consumer_started)
    {
        pthread_cond_wait(&cond_cs_mutex, &cs_mutex);
    }
    pthread_mutex_unlock(&cs_mutex);
    // std::cout << "P: Consumer started.\n";   

    // Process value
    int value = 0;
    while (std::cin >> value)
    {
        lock_data();
        while (data_updated) // if data yet updated we wait for consumer requires update.
            pthread_cond_wait(&upd_required_mtx, &data_mutex);
        data = value;
        data_updated = true;
        unlock_data();
    }
   
    lock_data();
    while (data_updated) // if data yet updated we wait for consumer requires update.
        pthread_cond_wait(&upd_required_mtx, &data_mutex);
    producer_ended = true;
    data = 0;
    data_updated = true;
    unlock_data();
    return 0;
}


void* consumer_routine(void* arg) 
{    
    pthread_mutex_lock(&cs_mutex);
    //sleep(2); // Wait 2 second;
    consumer_started = true;
    //sleep(2); // Wait another 2 seconds;
    pthread_cond_signal(&cond_cs_mutex);
    pthread_cond_signal(&cond_intr_mutex);
    //std::cout << "C: I am started.\n";
    pthread_mutex_unlock(&cs_mutex);
    
    // allocate value for result
    int* sum = new int(0);
    //return reinterpret_cast<void*>(sum);
    // for every update issued by producer, read the value and add to sum
    bool ended = false;
    while (!ended)
    { 
        lock_data();
        if (data_updated)
        {
            *sum += data;
            data = 0;
            data_updated = false;       
            ended = producer_ended;
            pthread_cond_signal(&upd_required_mtx);
        } 
        unlock_data();
    }    
    return static_cast<void*>(sum);

// return pointer to result
}


void* consumer_interruptor_routine(void* arg) 
{
    //return 0;
    // std::cout << "I: Wait for consumer...\n";
    pthread_mutex_lock(&cs_mutex);
    while (!consumer_started)
    {
        pthread_cond_wait(&cond_intr_mutex, &cs_mutex);
    }
    pthread_mutex_unlock(&cs_mutex);
    // std::cout << "I: Consumer started.\n";
    
    // interrupt consumer while producer is running
    pthread_t* consumer_pthread = static_cast<pthread_t *>(arg);    
   
    while (true)
    {
        bool ended = false;
        lock_data();
        if (producer_ended)
            ended = true;
        if (!ended)
            pthread_cancel(*consumer_pthread);
        unlock_data();
        if (ended) break;
    }
 
    //while (!producer_ended)
    //    pthread_cancel(*consumer_pthread);

    return 0;
}

int run_threads() 
{
    pthread_t consumer_thread;
    pthread_create(&consumer_thread, NULL, consumer_routine, NULL);

    pthread_t producer_thread;
    pthread_create(&producer_thread, NULL, producer_routine, NULL);

    pthread_t interruptor_thread;
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, &consumer_thread);
    
    int* p_sum = nullptr;  

    pthread_join(producer_thread, NULL);
    pthread_join(interruptor_thread, NULL);
    pthread_join(consumer_thread, reinterpret_cast<void **>(&p_sum));
    
    int sum = 0;
    if (p_sum) 
    {
        sum = *p_sum;
        delete p_sum;
    } 
    return sum;
}

int main() 
{
    std::cout << run_threads() << std::endl;
    return 0;
}
