#include <pthread.h>
#include <iostream>
#include <thread>
#include <vector>

using namespace std;

class Value {
public:
    Value() : _value(0) { }

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }

private:
    int _value;
};

pthread_mutex_t mutex_t;
pthread_cond_t producer_updated_value_t;
pthread_cond_t consumer_read_value_t;
pthread_cond_t consumer_started_t;

bool last_update = false;
bool consumer_is_ready = false;
bool consumer_started = false;


void *producer_routine(void *arg) {
    // Wait for consumer to start
    pthread_mutex_lock(&mutex_t);
    while (!consumer_started) {
        pthread_cond_wait(&consumer_started_t, &mutex_t);
    }
    pthread_mutex_unlock(&mutex_t);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    Value *value = static_cast<Value *>(arg);
    std::vector<int> data;
    int a;
    while (std::cin >> a) {
        data.push_back(a);
    }

    cout << "Producer thread is ready\n";


    for (int i = 0; i < data.size(); ++i) {
        pthread_mutex_lock(&mutex_t);
        value->update(data[i]);
        printf("Producer thread: Value update to %d, send signal to consumer\n", value->get());
        if (i + 1 == data.size()) {
            last_update = true;
        }

        pthread_cond_signal(&producer_updated_value_t);

        //wait for consumer to process
        while (!consumer_is_ready) {
            pthread_cond_wait(&consumer_read_value_t, &mutex_t);
        }


        consumer_is_ready = false;
        pthread_mutex_unlock(&mutex_t);
    }


    pthread_exit(EXIT_SUCCESS);

}

void *consumer_routine(void *arg) {
    // notify about start
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    consumer_started = true;
    pthread_cond_broadcast(&consumer_started_t);
    // allocate value for result
    Value *value = static_cast<Value *>(arg);

    int *res = new int;
    // for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&mutex_t);

        pthread_cond_wait(&producer_updated_value_t, &mutex_t);
        printf("Consumer thread: Condition signal received, value update to %d\n", value->get());
        *res += value->get();

        consumer_is_ready = true;
        printf("Consumer thread: send signal that sum was updated by %d\n", *res);
        pthread_cond_signal(&consumer_read_value_t);

        pthread_mutex_unlock(&mutex_t);

        if (last_update) {
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            // return pointer to result
            pthread_exit((void *) res);
        }
    }
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start
    pthread_mutex_lock(&mutex_t);
    while (!consumer_started) {
        pthread_cond_wait(&consumer_started_t, &mutex_t);
    }
    pthread_mutex_unlock(&mutex_t);

    while (!last_update) {
        int is_interrupted = pthread_cancel(*static_cast<pthread_t *>(arg));
        if (is_interrupted == 1) {
            cout << "!!!!!!!interrupted!!!!!!!!" << endl;
        }
    }

    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    cout << "Start 3 threads" << endl;

    Value value;

    int *res;
    pthread_t producer, consumer, interruptor;
    pthread_mutex_init(&mutex_t, NULL);
    pthread_cond_init(&producer_updated_value_t, NULL);
    pthread_cond_init(&consumer_read_value_t, NULL);

    pthread_create(&producer, NULL, producer_routine, (void *) &value);
    pthread_create(&consumer, NULL, consumer_routine, (void *) &value);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, &consumer);

    // start 3 threads and wait until they're done
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &res);
    pthread_join(interruptor, NULL);

    // return sum of update values seen by consumer
    return *res;
}

int main() {
    cout << "Start program" << endl;
    cout << run_threads() << endl;
    return 0;
}