#include <pthread.h>  
#include <iostream>
#include <sstream>

using namespace std;

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

pthread_t producer, consumer, interruptor;

bool consumerStarted, producerFinished, dataEnded, got, updated;
pthread_mutex_t mutexWait, mutexUpdate;
pthread_cond_t canStart, canUpdate;

void* producer_routine(void* arg) {
	// Wait for consumer to start
    
	pthread_mutex_lock(&mutexWait);
	
	if (!consumerStarted) {
	    pthread_cond_wait(&canStart, &mutexWait);
	}
	
    pthread_mutex_unlock(&mutexWait);
    
    //cout << "prod start\n";
	
    string input;
	getline(cin, input);   	 
	istringstream iss(input);

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *value = (Value *)arg;
    int newValue;
    dataEnded |= !(iss >> newValue);
    
    while (!dataEnded) {
    	pthread_mutex_lock(&mutexUpdate);
	
    	if (updated) {
			//cout << "prod see: " << newValue << '\n';
			
	    	pthread_cond_wait(&canUpdate, &mutexUpdate);
		
			value->update(newValue);
			
			dataEnded |= !(iss >> newValue);

			updated = 0;			
			got = 1;    	
    	}    	
    	
    	pthread_mutex_unlock(&mutexUpdate);			
    }
    
    producerFinished = 1;
	//cout << "prod end\n";
}

void* consumer_routine(void* arg) {
	//cout << "cons start\n";

	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
	
	pthread_mutex_lock(&mutexWait);
	
	consumerStarted = 1;
	pthread_cond_broadcast(&canStart);
	
    pthread_mutex_unlock(&mutexWait);
	
    // allocate value for result
    int *result = new int(0);
    Value *value = (Value *)arg;
    
    // for every update issued by producer, read the value and add to sum
    // return pointer to result
    while (!producerFinished) {
		pthread_mutex_lock(&mutexUpdate);
	
		if (got) {
			
			int newValue = value->get();
			//cout << "cons see: " << newValue << '\n';
			*result += newValue;
			
			updated = 1;
			got = 0;
		}
		
    	pthread_mutex_unlock(&mutexUpdate);			    	
    }
    
   	//cout << "cons end\n";
   	
   	return (void *) result;
}

void* consumer_interruptor_routine(void* arg) {
	//cout << "int start\n";
	
    // wait for consumer to start

	pthread_mutex_lock(&mutexWait);
	
	if (!consumerStarted) {
	    pthread_cond_wait(&canStart, &mutexWait);
	}
	
    pthread_mutex_unlock(&mutexWait);

    // interrupt consumer while producer is running
    while (!producerFinished) {
		pthread_cancel(consumer);    	
    }
    
   	//cout << "int end\n";
}

int run_threads() {
	consumerStarted = 0;
	producerFinished = 0;
	dataEnded = 0;
	got = 0;
	updated = 1;
	
	pthread_mutex_init(&mutexWait, NULL);
	pthread_cond_init(&canStart, NULL);
	
	Value *value = new Value();
	
	// start 3 threads and wait until they're done
    
	pthread_create(&producer, NULL, producer_routine, (void *)value);
	pthread_create(&consumer, NULL, consumer_routine, (void *)value);
	pthread_create(&interruptor, NULL, consumer_interruptor_routine, NULL);
	
    int status;
    void *result;

    status = pthread_join(producer, NULL);
    status = pthread_join(consumer, &result);
    status = pthread_join(interruptor, NULL);
    
    // return sum of update values seen by consumer

    return *(int *)result;
}

int main() {
    std::cout << run_threads() << std::endl;
    
	return 0;
}
