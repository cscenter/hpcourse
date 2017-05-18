#include <pthread.h>
#include <iostream>
#include <vector>
#include <sstream>


class Value {
public:
    Value() : _value(0), mutex(PTHREAD_MUTEX_INITIALIZER), changed(PTHREAD_COND_INITIALIZER),
              recorded(PTHREAD_COND_INITIALIZER){
        pthread_barrier_init(&barrier, NULL, 3);
    }

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }

    ~Value(){
        pthread_mutex_destroy(&mutex);
        pthread_cond_destroy(&changed);
        pthread_cond_destroy(&recorded);
        pthread_barrier_destroy(&barrier);
    }

    pthread_mutex_t mutex;
    pthread_cond_t changed;
    pthread_cond_t recorded;
    pthread_barrier_t barrier;

    bool memorized = true; // flag is true when consumer evaluated previous value
    bool finished = false; // flag is true when producer finished execution

private:
    int _value;

};

struct Producer_args{
    Value * value;
    int * numbers;
    int size;
    Producer_args(Value * _value, int * _numbers, int _size): value(_value), numbers(_numbers), size(_size) {}
};

struct Consumer_Interruptor_args{
    Value * value;
    pthread_t * pthread;
    Consumer_Interruptor_args(Value *_value, pthread_t * _pthread): value(_value), pthread(_pthread) {}
};

void* producer_routine(void * producer_args) {
    // Reading arguments
    Producer_args * args = (Producer_args *) producer_args;
    Value * value = args->value;
    int * numbers = args->numbers;
    int size = args->size;
    // Wait for consumer to start
    pthread_barrier_wait(&value->barrier);
    pthread_mutex_lock(&value->mutex);
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    for (size_t i = 0; i < size; i++) {
        while(!value->memorized) { // preventing spurious wakeup
            pthread_cond_wait(&value->recorded, &value->mutex);
        }
        // update value
        value->update(numbers[i]);
        value->memorized = false;
        pthread_cond_broadcast(&value->changed);
    }
    pthread_mutex_unlock(&value->mutex);
    value->finished = true;
    pthread_cond_signal(&value->changed);
}

void* consumer_routine(void * _value) {
    Value * value = (Value *) _value;
    struct timespec ts;
    // preventing cancelling
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    // notify about start
    pthread_barrier_wait(&value->barrier);
    // allocate value for result
    int result = 0; // simple int instead of Value
    // for every update issued by producer, read the value and add to sum
    for (;;) {
        pthread_mutex_lock(&value->mutex);
        while(value->memorized && !value->finished) {
            // wait for producer to evaluate
            pthread_cond_wait(&value->changed, &value->mutex);
        }
        if (value->finished && value->memorized) {
            // exit on condition if producer is done
            pthread_mutex_unlock(&value->mutex);
            break;
        }
        // update sum
        result += value->get();
        value->memorized = true;
        pthread_mutex_unlock(&value->mutex);
        pthread_cond_broadcast(&value->recorded);
    }
    // return pointer to result
    return (void *)result;
}

void* consumer_interruptor_routine(void * consumer_interruptor_args) {
    // reading arguments
    Consumer_Interruptor_args * args = (Consumer_Interruptor_args *) consumer_interruptor_args;
    Value * value = args->value;
    pthread_t * consumer_thread = args->pthread;
    // wait for consumer to start
    pthread_barrier_wait(&value->barrier);
    // interrupt consumer while producer is running
    while(!value->finished){ // while producer not done
        pthread_cancel(*consumer_thread);
    }
}

int run_threads(int * numbers, int size) {
    // start 3 threads and wait until they're done
    pthread_t producer_thread, consumer_thread, consumer_interruptor_thread;
    Value value;
    Producer_args producer_args(&value, numbers, size);
    Consumer_Interruptor_args consumer_interruptor_args(&value, &consumer_thread);
    pthread_create(&producer_thread, NULL, producer_routine, (void *)&producer_args);
    pthread_create(&consumer_interruptor_thread, NULL, consumer_interruptor_routine, (void *)&consumer_interruptor_args);
    pthread_create(&consumer_thread, NULL, consumer_routine, (void *)&value);
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_interruptor_thread, NULL);
    void * result;
    pthread_join(consumer_thread, &result);
    // return sum of update values seen by consumer
    return *((int*)(&result));
}


int main(int argc, char * argv[]) {
    std::vector<int> numbers;
    std::string data;
    std::getline(std::cin, data);
    std::stringstream string_stream(data);
    int x;
    while(true){
        string_stream >> x;
        if (!string_stream) break;
        numbers.push_back(x);
    }
    int n = numbers.size();
    std::cout << run_threads(numbers.data(), n) << std::endl;
    return 0;
}