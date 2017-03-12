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

bool consumer_started = false;
bool data_updated = false;
bool data_ended = false;


void *producer_routine(void *arg) {
    Value *data = (Value *) arg;

    // wait for consumer to start
    while (!consumer_started);

    int n;
    // read data, loop through each value
    while (std::cin >> n) {
        while (data_updated);      // wait for consumer to process previous value
        data->update(n);           // update value
        data_updated = true;       // notify consumer
    }

    while (data_updated);     // make sure last value is processed
    data_ended = true;

    pthread_exit(NULL);
}

void *consumer_routine(void *arg) {
    Value *data = (Value *) arg;

    // notify about start
    consumer_started = true;

    // allocate value for result
    int *result = new int(0);

    // for every update issued by producer, read the value and add to sum
    while (true) {
        while (!data_updated & !data_ended);      // wait for producer to read new value

        if (data_ended) break;

        *result += data->get();
        data_updated = false;
    }

    // return pointer to result
    return result;
}

void *consumer_interruptor_routine(void *arg) {
    // wait for consumer to start

    // interrupt consumer while producer is running
}

int run_threads() {
    pthread_t producer, consumer, consumer_interruptor;
    Value *data = new Value();

    // start threads
    pthread_create(&consumer_interruptor, NULL, consumer_interruptor_routine, (void *) consumer);
    pthread_create(&producer, NULL, producer_routine, (void *) data);
    pthread_create(&consumer, NULL, consumer_routine, (void *) data);

    // wait until they're done
    int *result;
    pthread_join(producer, NULL);
    pthread_join(consumer, (void**)&result);
    pthread_join(consumer_interruptor, NULL);

    // return sum of update values seen by consumer
    return *result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}