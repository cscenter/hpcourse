#include <cstdlib>
#include <iostream>
#include <pthread.h>
#include <list>
#include <vector>

pthread_t producer;
pthread_t consumer;
pthread_t interrupter;  

pthread_mutex_t       mutex = PTHREAD_MUTEX_INITIALIZER;
// notify that producer complete a part of work 
pthread_cond_t producer_signal = PTHREAD_COND_INITIALIZER;
// notify that consumer complete a processing a nunber 
pthread_cond_t consumer_complete_processing = PTHREAD_COND_INITIALIZER;
pthread_cond_t consumer_started = PTHREAD_COND_INITIALIZER;

int data = 0;

bool consumer_should_work = false;
bool all_is_done = false;
bool consumer_started_flag = false;

void *producer_routine(void* args) {
    
    std::list<int>* elements = (std::list<int>*) args;
    std::list<int>::iterator it;
  
    pthread_mutex_lock(&mutex);
    for (it = elements->begin(); it != elements->end(); ++it) {
        data = *it;
        consumer_should_work = true;
     
        // put new data
        pthread_cond_signal(&producer_signal);
        
        // wait until consumer complete processing
        while (consumer_should_work) {
            pthread_cond_wait(&consumer_complete_processing, &mutex);
        }
    }
    
    // need to call that consumer should work and all data were pushed
    consumer_should_work = true;
    all_is_done = true;
    pthread_cond_signal(&producer_signal);
    pthread_mutex_unlock(&mutex);
    
    pthread_exit(NULL);
}

void *consumer_routine(void* args) {
    // protect for cancelling
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    
    // notify that consumer started
    pthread_mutex_lock(&mutex);
    consumer_started_flag = true;
    pthread_cond_signal(&consumer_started);
    int* sum = (int*) args;
    while(true) {
        // waith for signal from producer
        while (!consumer_should_work) {
            pthread_cond_wait(&producer_signal, &mutex);
        }
       
        // if all_is_done we should exit
        if (all_is_done) {
            pthread_mutex_unlock(&mutex);
            pthread_exit((void *) sum);
        }
        
        *sum += data;
        consumer_should_work = false;
        
        // consumer complete part of job
        pthread_cond_signal(&consumer_complete_processing);
    }
    pthread_mutex_unlock(&mutex);
}

void *interrupter_routine(void* args) {
    
    pthread_mutex_lock(&mutex);
    // wait for consumer starting work
    while(!consumer_started_flag) {
        pthread_cond_wait(&consumer_started, &mutex);
    }
    pthread_mutex_unlock(&mutex);
    
    // while producer not finishing, should try to cancel consumer
    bool should_continue = true;
    while (should_continue) {
        pthread_cancel(consumer);
        pthread_mutex_lock(&mutex);
        if (all_is_done) {
            should_continue = false;
        }
        pthread_mutex_unlock(&mutex);
    }
    
    pthread_exit(NULL);
}

int run_threads() {
    
    std::list<int> elements;
    
    // add elements to
    int n = 0;
    while(std::cin >> n) {
        elements.push_back(n);
    }
    
    pthread_create(&producer, NULL, producer_routine, (void *) &elements);
    
    int sum = 0;
    pthread_create(&consumer, NULL, consumer_routine, (void *) &sum);
    
    pthread_create(&interrupter, NULL, interrupter_routine, NULL);
    
    pthread_join(producer, NULL);
    pthread_join(consumer, NULL);
    pthread_join(interrupter, NULL);
    
    return sum;
}

int main(int argc, char** argv) {
    int result = run_threads();
    std::cout << result;
    return 0;
}
