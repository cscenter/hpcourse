#include <pthread.h>
#include <iostream>

using namespace std;

enum State { CONSUMER_STARTED, CONSUMER_UPDATED, PRODUCER_ENDED, DATA_FOR_UPDATE, NONE };

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

pthread_mutex_t mutex;
pthread_cond_t consumer_started, data_r;
State current_state = NONE;

void* producer_routine(void* arg) {
    // Wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (current_state != CONSUMER_STARTED) {
        pthread_cond_wait(&consumer_started, &mutex);
    }
    pthread_mutex_unlock(&mutex);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int number = 0;
    while (cin >> number) {
        pthread_mutex_lock(&mutex);
        ((Value* ) arg) -> update(number);
        current_state = DATA_FOR_UPDATE;
        //send signal to consumer
        pthread_cond_signal(&data_r);
        while(current_state != CONSUMER_UPDATED) {
            pthread_cond_wait(&data_r, &mutex);
        }
        pthread_mutex_unlock(&mutex);
    }
    //ended work, we have to change current state and send signal to cunsumer
    pthread_mutex_lock(&mutex);
    current_state = PRODUCER_ENDED;
    pthread_cond_signal(&data_r);
    pthread_mutex_unlock(&mutex);
    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    //don't interrupt
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    // notify about start
    pthread_mutex_lock(&mutex);
    current_state = CONSUMER_STARTED;
    // to wake up interruptor and producer use broadcast
    pthread_cond_broadcast(&consumer_started);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    int* sum = new int(0);
    // for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&mutex);
        //wait for ready data
        while(current_state != DATA_FOR_UPDATE && current_state != PRODUCER_ENDED) {
            pthread_cond_wait(&data_r, &mutex);
        }
        if (current_state == PRODUCER_ENDED) {
            // cout << "inside producer end";
            //we have to unlock mutex, because we break cycle
            pthread_mutex_unlock(&mutex);
            break;
        } else {
            // cout << "inside updating";
            *sum += ((Value*)arg) -> get();
            //consumer update value
            current_state = CONSUMER_UPDATED;
        }
        //notify producer update was succesfully ended
        pthread_cond_signal(&data_r);
        pthread_mutex_unlock(&mutex);
    }
    //can interrupt
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
    // return pointer to result
    pthread_exit((void*) sum);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (current_state != CONSUMER_STARTED) {
        pthread_cond_wait(&consumer_started, &mutex);
    }
    pthread_mutex_unlock(&mutex);
    //get consumer's pthread
    pthread_t* cons = (pthread_t *) arg;
    // interrupt consumer while producer is running
    while (current_state != PRODUCER_ENDED) {
        pthread_cancel(*cons);
    }
    pthread_exit(NULL);
}

int run_threads() {
    pthread_t prod, cons, interrupt;
    int *result;
    Value value;

    //init mutex and conditions
    pthread_mutex_init(&mutex, NULL);
    pthread_cond_init(&data_r, NULL);
    pthread_cond_init(&consumer_started, NULL);

    // start 3 threads and wait until they're done
    int retcode = 0;
    if ((retcode = pthread_create(&prod, NULL, producer_routine, (void *) &value))) {
        cerr << "ERROR; return code from pthread_create() for producer is " << retcode << endl;
        exit(-1);
    }
    if ((retcode = pthread_create(&cons, NULL, consumer_routine, (void *) &value))) {
        cerr << "ERROR; return code from pthread_create() for consumer is " << retcode << endl;
        exit(-1);
    }
    if ((retcode = pthread_create(&interrupt, NULL, consumer_interruptor_routine, (void *) &cons))) {
        cerr << "ERROR; return code from pthread_create() for interruptor is " << retcode << endl;
        exit(-1);
    }

    //wait for all
    pthread_join(prod, NULL);
    pthread_join(cons, (void **) &result);
    pthread_join(interrupt, NULL);

    // return sum of update values seen by consumer
    int sum = *result;
    delete result;
    //free resources
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&data_r);
    pthread_cond_destroy(&consumer_started);
    return sum;
}

int main() {
    cout << run_threads() << endl;
    return 0;
}
