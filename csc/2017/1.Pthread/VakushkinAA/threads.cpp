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

pthread_mutex_t producer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumer_ready_mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_cond_t wait_for_consumer = PTHREAD_COND_INITIALIZER;

pthread_barrier_t barrier;

bool still_has_data = true;


void* producer_routine(void* arg) {
    long long input = 0;
    Value * v = (Value *) arg;

    // Wait for consumer to start
    pthread_barrier_wait(&barrier);    
  
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process    
    while(std::cin >> input) {
        // process input
        pthread_mutex_lock(&producer_mutex);
        v->update(input);
        pthread_mutex_unlock(&consumer_mutex);
        
        // wait for consumer to acknowledge input
        pthread_mutex_lock(&consumer_ready_mutex);
        pthread_cond_wait(&wait_for_consumer, &consumer_ready_mutex);
        pthread_mutex_unlock(&consumer_ready_mutex);
    }
    
    // exit gracefully (i.e. allow consumer to exit as well)
    still_has_data = false;
    pthread_mutex_unlock(&consumer_mutex);
    pthread_exit(0);
}

void* consumer_routine(void* arg) {
    // disallow interruption
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    
    Value * v = (Value *) arg;
    
    // notify about start
    pthread_barrier_wait(&barrier); 
      
    // allocate value for result
    long long * result = new long long(0);

    
    // for every update issued by producer, read the value and add to sum
    while(true) {
        // process input from producer
        pthread_mutex_lock(&consumer_mutex);
        if(still_has_data) 
            *result += v->get();
        else
            break;
            
        // acknowledge input from consumer
        pthread_mutex_unlock(&producer_mutex);
        pthread_cond_signal(&wait_for_consumer);
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

  	// consumer_mutex starts locked so that we do not try to process input before getting one
  	pthread_mutex_lock(&consumer_mutex);

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