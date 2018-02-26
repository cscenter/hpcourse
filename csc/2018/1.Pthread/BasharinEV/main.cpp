#include <pthread.h>  
#include <iostream>

struct Data
{
    bool end;      // True if comsumer ends updating.
    bool updated;  // True if data updated by comsumer.
    int value;

    pthread_mutex_t mutex; 
};

bool consumer_started = false;
Data data_{false, false, 0};

void* producer_routine(void* arg) 
{
    // Wait for consumer to start
    while (!consumer_started) 
    {}
    int value = 0;
    while (std::cin >> value)
    {
        while (data_.updated) {}
    
        pthread_mutex_lock(&data_.mutex);
        data_.value = value;
        data_.updated = true;
        pthread_mutex_unlock(&data_.mutex);
    }
    while (data_.updated) {}
    data_.end = true;
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
}


void* consumer_routine(void* arg) 
{    
    // notify about start
    consumer_started = true;
    // allocate value for result
    int sum = 0;
    
    // for every update issued by producer, read the value and add to sum

    while (true)
    {

        if (data_.end) 
        
        {
            return reinterpret_cast<void *>(sum);
        }
        if (data_.updated)
        {
            pthread_mutex_lock(&data_.mutex);
            sum += data_.value;
            data_.updated = false;
            pthread_mutex_unlock(&data_.mutex); 
        }
    }

// return pointer to result
}

void* consumer_interruptor_routine(void* arg) 
{
    // wait for consumer to start
    while (!consumer_started) {}

    // interrupt consumer while producer is running
    pthread_t* consumer_pthread = static_cast<pthread_t *>(arg);    

    while (!data_.end)
    {

        // No one thread can update updated-flag while during this action
        // because interruptror tries to cancel consumer when data_.updated == false;
        pthread_mutex_lock(&data_.mutex);

        if(!data_.updated)
        {
            pthread_cancel(*consumer_pthread);
        }
        
        pthread_mutex_unlock(&data_.mutex);
    }
}

int run_threads() 
{
    pthread_t consumer_thread;
    pthread_create(&consumer_thread, NULL, consumer_routine, NULL);

    pthread_t producer_thread;
    pthread_create(&producer_thread, NULL, producer_routine, NULL);

    pthread_t interruptor_thread;
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, &consumer_thread);
    
    int sum = 0;  

    pthread_join(producer_thread, NULL);
    pthread_join(interruptor_thread, NULL);
    pthread_join(consumer_thread, (void **)(&sum));
    return sum;
}

int main() 
{
    std::cout << run_threads() << std::endl;
    return 0;
}
