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

bool consumerStarted, producerFinished, dataEnded, producerIsEnabled;
pthread_mutex_t mutexWait, mutexUpdate;
pthread_cond_t canStart, waitForConsumer, waitForProducer;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    
    pthread_mutex_lock(&mutexWait);
    
    while (!consumerStarted) {
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
    
    while (!dataEnded) {
        pthread_mutex_lock(&mutexUpdate);
    
        while (!producerIsEnabled) {
            pthread_cond_wait(&waitForConsumer, &mutexUpdate);    
        }
        
        dataEnded |= !(iss >> newValue);
        
        //cout << "prod see: " << newValue << '\n';
        
        if (!dataEnded) {
            value->update(newValue);
            producerIsEnabled = 0;
        }

        pthread_cond_signal(&waitForProducer);
        
        pthread_mutex_unlock(&mutexUpdate);         
    }
    
    pthread_mutex_lock(&mutexUpdate);
    
    producerFinished = 1;
    
    pthread_cond_signal(&waitForProducer);
    
    pthread_mutex_unlock(&mutexUpdate);
           
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
    
        while (producerIsEnabled && !producerFinished) {
            pthread_cond_wait(&waitForProducer, &mutexUpdate);    
        }                                            

        if (!producerFinished) {  
            int newValue = value->get();
            //cout << "cons see: " << newValue << '\n';
            *result += newValue;
            
            producerIsEnabled = 1;
        
            pthread_cond_signal(&waitForConsumer);
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
    
    while (!consumerStarted) {
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
    producerIsEnabled = 1;
    
    pthread_mutex_init(&mutexWait, NULL);
    pthread_mutex_init(&mutexUpdate, NULL);
    pthread_cond_init(&canStart, NULL);
    pthread_cond_init(&waitForConsumer, NULL);
    pthread_cond_init(&waitForProducer, NULL);
    
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

    int* pointerToResult = (int *)result;
    int sum = *pointerToResult;
    
    delete pointerToResult;
    
    pthread_mutex_destroy(&mutexWait);
    pthread_mutex_destroy(&mutexUpdate);
    pthread_cond_destroy(&canStart);
    pthread_cond_destroy(&waitForConsumer);
    pthread_cond_destroy(&waitForProducer);

    return sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    
    return 0;
}
