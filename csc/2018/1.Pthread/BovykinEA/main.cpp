#include <pthread.h>  
#include <iostream>
#include <vector>

using namespace std;

int new_data;
bool is_data_updated = false;
pthread_mutex_t data_mutex_send = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  data_cond_send  = PTHREAD_COND_INITIALIZER;

pthread_mutex_t data_mutex_get = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  data_cond_get  = PTHREAD_COND_INITIALIZER;

bool is_consumer_started = false;
pthread_mutex_t consumer_started_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  consumer_started_cond  = PTHREAD_COND_INITIALIZER;

bool is_producer_finished = false;
pthread_mutex_t producer_finished_mutex = PTHREAD_MUTEX_INITIALIZER;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    /*
    pthread_mutex_lock(&consumer_started_mutex);

    while(!is_consumer_started){
        pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
    }

    pthread_mutex_unlock(&consumer_started_mutex);
    */

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    vector<int> data;
    
    int v;

    while(cin >> v){
        data.push_back(v);
    }

    for(int i = 0; i < data.size(); i++){
        pthread_mutex_lock(&data_mutex_send);

        new_data = data[i];

            /*
        int incoming_data;

        if(!(cin >> incoming_data)){
            is_producer_finished = true;
            new_data = 0;
        } else {
            new_data = incoming_data;
        }
        */

        if(i == data.size() - 1){
            pthread_mutex_lock(&producer_finished_mutex);
            is_producer_finished = true;
            pthread_mutex_unlock(&producer_finished_mutex);
        }

        is_data_updated = true;

        pthread_cond_signal(&data_cond_send);
        pthread_mutex_unlock(&data_mutex_send);
        
        pthread_mutex_lock(&data_mutex_get);
        while(is_data_updated){
            pthread_cond_wait(&data_cond_get, &data_mutex_get);
        }
        pthread_mutex_unlock(&data_mutex_get);

    }
}

void* consumer_routine(void* arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, 0);

    pthread_mutex_lock(&consumer_started_mutex);
    is_consumer_started = true;
    pthread_cond_broadcast(&consumer_started_cond);
    pthread_mutex_unlock(&consumer_started_mutex);

    int sum = 0;
    int exit = false;

    while(!exit){
        pthread_mutex_lock(&data_mutex_send);

        while(!is_data_updated){
            pthread_cond_wait(&data_cond_send, &data_mutex_send);
        }

        sum += new_data;

        pthread_mutex_lock(&producer_finished_mutex);
        exit = is_producer_finished;
        pthread_mutex_unlock(&producer_finished_mutex);

        pthread_mutex_unlock(&data_mutex_send);

        pthread_mutex_lock(&data_mutex_get);
        is_data_updated = false;
        pthread_cond_signal(&data_cond_get);
        pthread_mutex_unlock(&data_mutex_get);
    }

    return new int(sum);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_mutex_lock(&consumer_started_mutex);

    while(!is_consumer_started){
        pthread_cond_wait(&consumer_started_cond, &consumer_started_mutex);
    }

    pthread_mutex_unlock(&consumer_started_mutex);

    // interrupt consumer while producer is running
    pthread_t* consumer = (pthread_t *)arg;

    bool exit = false;

    while(!exit){
        pthread_cancel(*consumer);

        pthread_mutex_lock(&producer_finished_mutex);
        exit = is_producer_finished;
        pthread_mutex_unlock(&producer_finished_mutex);
    }
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer

    int* sum = 0;

    pthread_t producer;
    pthread_t consumer;
    pthread_t interruptor;

    pthread_create(&consumer, nullptr, consumer_routine, nullptr);
    pthread_create(&interruptor, nullptr, consumer_interruptor_routine, &consumer);
    pthread_create(&producer, nullptr, producer_routine, nullptr);

    pthread_join(consumer, (void **)&sum);
    pthread_join(interruptor, nullptr);
    pthread_join(producer, nullptr);

    pthread_mutex_destroy(&data_mutex_send);
    pthread_mutex_destroy(&data_mutex_get);
    pthread_mutex_destroy(&consumer_started_mutex);

    pthread_cond_destroy(&data_cond_send);
    pthread_cond_destroy(&data_cond_get);
    pthread_cond_destroy(&consumer_started_cond);

    return *sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
