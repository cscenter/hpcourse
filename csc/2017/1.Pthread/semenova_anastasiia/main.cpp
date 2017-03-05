#include <pthread.h>
#include <iostream>
#include <vector>
#include <sstream>
#include <iterator>
#include <unistd.h>

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;

pthread_t producer;
pthread_t consumer;
pthread_t interruptor;

pthread_cond_t value_is_set_by_producer_cond;
pthread_cond_t value_is_read_by_consumer_cond;
pthread_cond_t consumer_is_ready_cond;
pthread_barrier_t start_barrier;

bool value_is_set_by_producer = false;
bool value_is_read_by_consumer = true;
bool producer_did_last_update = false;
bool consumer_is_ready = false;

int number_of_ints = 0;

bool update_is_last = false;

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

void* producer_routine(Value* value) {
    // Read data
    std::string line;
    getline(std::cin, line);
    std::istringstream is(line);
    std::vector<int> input = (std::vector<int>(std::istream_iterator<int>(is),
                                     std::istream_iterator<int>()));

    number_of_ints = input.size();

    // Wait for consumer to start
    pthread_barrier_wait(&start_barrier);

    // Loop through each value and update the value, notify consumer, wait for consumer to process
    for (auto next_int : input) {
        pthread_mutex_lock(&mutex);

        value->update(next_int);
        value_is_set_by_producer = true;
        value_is_read_by_consumer = false;

        pthread_cond_signal(&value_is_set_by_producer_cond);

        while (!value_is_read_by_consumer) {
            pthread_cond_wait(&value_is_read_by_consumer_cond, &mutex);
        }

        pthread_mutex_unlock(&mutex);
    }
    producer_did_last_update = true;

    return NULL;
}

void* consumer_routine(Value* value) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    // allocate value for result
    int* sum = new int;
    *sum = 0;

    // notify about start (= wait with others until everybody are ready)
    pthread_barrier_wait(&start_barrier);

    // for every update issued by producer, read the value and add to sum
    for (int i = 0; i < number_of_ints; ++i) {
        pthread_mutex_lock(&mutex);

        while (!value_is_set_by_producer) {
            pthread_cond_wait(&value_is_set_by_producer_cond, &mutex);
        }

        int new_value = value->get();
        *sum += new_value;
        value_is_read_by_consumer = true;
        value_is_set_by_producer = false;

        pthread_cond_signal(&value_is_read_by_consumer_cond);

        pthread_mutex_unlock(&mutex);
    }

    // return pointer to result
    return (void*) sum;
}

void* consumer_interruptor_routine(void*) {

    pthread_barrier_wait(&start_barrier);

    // interrupt consumer while producer is running
    while (!producer_did_last_update) {
        pthread_cancel(consumer);
    }

    return NULL;
}

int run_threads() {
    pthread_cond_init(&value_is_read_by_consumer_cond, NULL);
    pthread_cond_init(&value_is_set_by_producer_cond, NULL);
    pthread_barrier_init(&start_barrier, NULL, 3);

    // start 3 threads and wait until they're done
    Value* value = new Value;
    pthread_create(&consumer, NULL, (void * (*)(void *)) consumer_routine, value);
    pthread_create(&producer, NULL, (void * (*)(void *)) producer_routine, value);

    pthread_create(&interruptor, NULL, (void * (*)(void *)) consumer_interruptor_routine, value);

    int* res;
    pthread_join(producer, (void **) &res);
    pthread_join(consumer, (void **) &res);

    delete value;

    // return sum of update values seen by consumer
    return *res;
}

int main() {
    std::cout << run_threads() << std::endl;

    return 0;
}
