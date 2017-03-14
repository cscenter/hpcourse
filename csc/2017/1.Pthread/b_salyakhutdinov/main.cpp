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

// Global state ftw!
pthread_t producer, consumer, interruptor;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

bool consumer_started = false;
bool value_ready = false;
bool producer_ready = false;
Value value;

void* producer_routine(void* arg) {
    Value &value = *(Value*)arg;
    // Wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (!consumer_started)
	pthread_cond_wait(&cond, &mutex);
    pthread_mutex_unlock(&mutex);

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process

    while (!producer_ready) {
	int x;
	pthread_mutex_lock(&mutex);

	while (value_ready) {
	    pthread_cond_wait(&cond, &mutex);
	}  

	std::cin >> x;

	if (std::cin) 
	    value.update(x);
	else 
	    producer_ready = true;
	
	value_ready = true;

	pthread_cond_signal(&cond);

	pthread_mutex_unlock(&mutex);
    }

    return NULL;
}

void* consumer_routine(void* arg) {
    Value &value = *(Value*)arg;
    
    // notify about start
    pthread_mutex_lock(&mutex);
    std::cout << "C started\n";
    consumer_started = true;
    pthread_cond_broadcast(&cond);
    pthread_mutex_unlock(&mutex);
    
    // allocate value for result
    int *result = (int*)malloc(sizeof(int));
    *result = 0;
    
    // for every update issued by producer, read the value and add to sum
    bool ready = false;
    while(!ready) {
	int x;

	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	pthread_mutex_lock(&mutex);

	while (!value_ready)
	    pthread_cond_wait(&cond, &mutex);

	if (!producer_ready) {
	    x = value.get();
	    value_ready = false;
	    pthread_cond_signal(&cond);
	} else
	    ready = true;
	
	pthread_mutex_unlock(&mutex);
	pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);

	if (!ready)
	    *result += x;
    }
    
    // return pointer to result
    return (void*)result;
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (!consumer_started) {
	pthread_cond_wait(&cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    // interrupt consumer while producer is running
    bool ready = false;
    while (!ready) {
	if (pthread_cancel(consumer)) {
	    ready = true;
	    break;
	}
        pthread_mutex_lock(&mutex);
	if (producer_ready) {
	    ready = true;
	}
	pthread_mutex_unlock(&mutex);
    }

    return NULL;
}

int run_threads() {
    // start 3 threads and wait until they're done
    int errcode = 0;
    errcode |= pthread_create(&producer, NULL, producer_routine, &value);
    errcode |= pthread_create(&consumer, NULL, consumer_routine, &value);
    //errcode |= pthread_create(&interruptor, NULL, consumer_interruptor_routine, NULL);

    if (errcode) {
	std::cerr << "Couldn't start threads. Exit..." << std::endl;
	return 1;
    }

    // return sum of update values seen by consumer

    void *result_ptr;
    
    pthread_join(producer, NULL);
    pthread_join(consumer, &result_ptr);
    pthread_join(consumer, NULL);

    int ret = *(int*)result_ptr;
    free(result_ptr);
    
    return ret;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
