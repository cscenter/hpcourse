#include <iostream>
#include <pthread.h>
#include <vector>
#include <climits>


#define NOERROR 0
#define OVERFLOW 1

class SharedData {
public:
    SharedData() : m_data(0) {}

    int get() const {
        return m_data;
    }

    void update(int data) {
        m_data = data;
    }

private:
    int m_data;
};

typedef struct Result {
    int partial_sum;
    int error_code;
} Result;

thread_local int current_error_code = NOERROR;

int number_of_consumers = 0;
int max_sleep = 0;

int number_of_running_consumers = 0;
pthread_mutex_t running_consumers_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t  running_consumers_cond  = PTHREAD_COND_INITIALIZER;

pthread_mutex_t update_shared_data_mutex = PTHREAD_MUTEX_INITIALIZER;

int get_last_error() {
    // return per-thread error code
    return current_error_code;
}


void set_last_error(int code) {
    // set per-thread error code
    current_error_code = code;
}


void wait_for_consumers_to_start() {
    pthread_mutex_lock(&running_consumers_mutex);
    while (number_of_running_consumers != number_of_consumers) {
        pthread_cond_wait(&running_consumers_cond, &running_consumers_mutex);
    }
    pthread_mutex_unlock(&running_consumers_mutex);
}


void* producer_routine(void* arg) {
    // wait for consumer to start
    wait_for_consumers_to_start();

    // read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int number = 0;
    while(std::cin >> number) {
        pthread_mutex_lock(&update_shared_data_mutex);
        ((SharedData*) arg)->update(number);
        pthread_mutex_unlock(&update_shared_data_mutex);
    }
}


bool is_overflow(int partial_sum, int value_to_add) {
    return (value_to_add < 0 && partial_sum < INT_MIN - value_to_add) ||
           (value_to_add > 0 && partial_sum > INT_MAX - value_to_add);
}

void* consumer_routine(void* arg) {
    // notify about start
    pthread_mutex_lock(&running_consumers_mutex);
    number_of_running_consumers++;
    pthread_mutex_unlock(&running_consumers_mutex);

    // for every update issued by producer, read the value and add to sum
    int* partial_sum = new int(0);

    // return pointer to result (for particular consumer)
    pthread_exit((void*) partial_sum);
}


void* consumer_interruptor_routine(void* arg) {
    // wait for consumers to start
    wait_for_consumers_to_start();
    // interrupt random consumer while producer is running
}


int run_threads() {
    int sum = 0;
    SharedData shared_data;
    pthread_t producer;
    pthread_t consumers[number_of_consumers];

    // start N threads
    int code = 0;

    code = pthread_create(&producer, nullptr, producer_routine, (void*) &shared_data);
    if (code) {
        std::cerr << "Producer phtread_create() has error: " << code << std::endl;
        exit(-1);
    }

    for (int i = 0; i < number_of_consumers; i++) {
        code = pthread_create(&consumers[i], nullptr, consumer_routine, (void*) &shared_data);
        if (code) {
            std::cerr << "Consumers phtread_create() has error: " << code << std::endl;
            exit(-1);
        }
    }

    // and wait until they're done
    pthread_join(producer, nullptr);

    Result* result;
    for (int i = 0; i < number_of_consumers; i++) {
        pthread_join(consumers[i], (void**) &result);
        if (is_overflow(sum, result->partial_sum) || result->error_code == OVERFLOW) {
            current_error_code = OVERFLOW;
        } else {
            sum += result->partial_sum;
        }
    }

    if (current_error_code == OVERFLOW) {
        std::cout << "overflow" << std::endl;
        return 1;
    }

    // return aggregated sum of values
    std::cout << sum << std::endl;
    return 0;
}


int main(int argc, char* argv[]) {
    number_of_consumers = atoi(argv[1]);
    max_sleep = atoi(argv[2]);
    return run_threads();
}
