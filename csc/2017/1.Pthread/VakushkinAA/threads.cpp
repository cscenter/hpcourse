#include <pthread.h>  
#include <iostream>

class Value {
public:
    Value() : _value(0) {}

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }
private:
    int _value;
};

const int number_of_threads = 3;

pthread_mutex_t value_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t producer_finished_mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t wait_for_consumer = PTHREAD_COND_INITIALIZER;
pthread_cond_t wait_for_producer = PTHREAD_COND_INITIALIZER;
pthread_cond_t producer_finished = PTHREAD_COND_INITIALIZER;

pthread_barrier_t barrier;

bool still_has_data = true;
bool wait_for_consumer_flag = false;
bool wait_for_producer_flag = false;

void* producer_routine(void* arg) {
    long long input = 0;
    Value * v = (Value *) arg;
    pthread_mutex_t consumer_ready_mutex = PTHREAD_MUTEX_INITIALIZER;

    // Wait for consumer to start
    pthread_barrier_wait(&barrier);    
  
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process    
    while(std::cin >> input) {
        pthread_mutex_lock(&value_mutex);
        v->update(input);
        wait_for_producer_flag = true;
        pthread_cond_signal(&wait_for_producer);
        pthread_mutex_unlock(&value_mutex);
        
        // wait for consumer to acknowledge input
        pthread_mutex_lock(&consumer_ready_mutex);
        while(!wait_for_consumer_flag) {            
            pthread_cond_wait(&wait_for_consumer, &consumer_ready_mutex);
        }
        wait_for_consumer_flag = false;
        pthread_mutex_unlock(&consumer_ready_mutex);
    }
    
    // exit gracefully (i.e. allow consumer to exit as well)
    pthread_mutex_lock(&producer_finished_mutex);
    
    still_has_data = false;
    wait_for_producer_flag = true;
    pthread_cond_signal(&wait_for_producer);

    pthread_mutex_unlock(&producer_finished_mutex);

    pthread_exit(0);
}

void* consumer_routine(void* arg) {
    // disallow interruption
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    
    Value * v = (Value *) arg;
    // allocate value for result
    long long * result = new long long(0);
    bool finished = false;
    pthread_mutex_t producer_ready_mutex = PTHREAD_MUTEX_INITIALIZER;
    // notify about start
    pthread_barrier_wait(&barrier);     
    
    // for every update issued by producer, read the value and add to sum
    while(true) {
        pthread_mutex_lock(&producer_ready_mutex);
        while(!wait_for_producer_flag) {            
            pthread_cond_wait(&wait_for_producer, &producer_ready_mutex);
        }
        wait_for_producer_flag = false;
        pthread_mutex_unlock(&producer_ready_mutex);

        // process input from producer
        pthread_mutex_lock(&value_mutex);

        pthread_mutex_lock(&producer_finished_mutex);
        if(still_has_data) {
            *result += v->get();
        }
        else {
            finished = true;
        }
        pthread_mutex_unlock(&producer_finished_mutex);

        if(finished) {
            pthread_mutex_unlock(&value_mutex);
            break;
        }
        //std::cout << "[csm] input consumed, got " << v->get() << std::endl;
            
        // acknowledge input from consumer
        wait_for_consumer_flag = true;
        pthread_cond_signal(&wait_for_consumer);
        pthread_mutex_unlock(&value_mutex);        
    }
    
    // return pointer to result  
    pthread_exit((void *) result);
}


void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_barrier_wait(&barrier); 
    
    // interrupt consumer while producer is running
    while(!pthread_cancel(*(pthread_t *) arg)) ;        
        
    pthread_exit(0);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    
    pthread_t producer_thread, consumer_thread, interruptor_thread;
    Value v;
    
    pthread_barrier_init(&barrier, nullptr, number_of_threads);

    pthread_create(&producer_thread, NULL, producer_routine, (void *) &v);
    pthread_create(&consumer_thread, NULL, consumer_routine, (void *) &v);
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, (void *) &consumer_thread);

    pthread_barrier_destroy(&barrier);

    
    // get result   
    long long * p_result;
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, (void **) &p_result);
    pthread_join(interruptor_thread, NULL);
    
    long long result = *p_result;
    delete p_result;
        
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}