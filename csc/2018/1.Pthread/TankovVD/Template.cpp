#include <pthread.h>
#include <iostream>

//producer
pthread_t producer_routine_t;

//consumer
pthread_mutex_t consumer_routine_t_running_mutex = PTHREAD_MUTEX_INITIALIZER;
bool consumer_routine_t_running = false;
pthread_t consumer_routine_t;

//use spin lock cause consumer should start momentally
void wait_consumer() {
    bool consumer_running_local = false;
    while (!consumer_running_local) {
        pthread_mutex_lock(&consumer_routine_t_running_mutex);
        consumer_running_local = consumer_routine_t_running;
        pthread_mutex_unlock(&consumer_routine_t_running_mutex);
    }
}

//read-wrapper around consumer_routine_t_running
//not sure that we need it, bool is less than a word and should be accessed atomically
bool is_consumer_running() {
    bool consumer_running_local;
    pthread_mutex_lock(&consumer_routine_t_running_mutex);
    consumer_running_local = consumer_routine_t_running;
    pthread_mutex_unlock(&consumer_routine_t_running_mutex);
    return consumer_running_local;
}

//write-wrapper around consumer_routine_t_running
void set_consumer_running(bool consumer_running_local) {
    pthread_mutex_lock(&consumer_routine_t_running_mutex);
    consumer_routine_t_running = consumer_running_local;
    pthread_mutex_unlock(&consumer_routine_t_running_mutex);
}


//producer_consumer
bool end_data = false;
bool got_data = false;
int data = 0;
pthread_mutex_t producer_to_consumer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t producer_to_consumer_cond = PTHREAD_COND_INITIALIZER;

//consumer_producer
bool received_data = false;
pthread_mutex_t consumer_to_producer_mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t consumer_to_producer_cond = PTHREAD_COND_INITIALIZER;

//interruptor
pthread_t consumer_interruptor_routine_t;


void *producer_routine(void *arg) {
    wait_consumer();

    int data_size = 0;
    std::cin >> data_size;
    for (int i = 0; i < data_size; i++) {
        //send data
        pthread_mutex_lock(&producer_to_consumer_mutex);

        std::cin >> data;
        got_data = true;

        pthread_cond_signal(&producer_to_consumer_cond);
        pthread_mutex_unlock(&producer_to_consumer_mutex);


        //get confirmation
        pthread_mutex_lock(&consumer_to_producer_mutex);
        while (!received_data) {
            pthread_cond_wait(&consumer_to_producer_cond, &consumer_to_producer_mutex);
        }

        received_data = false;

        pthread_mutex_unlock(&consumer_to_producer_mutex);
    }

    pthread_mutex_lock(&producer_to_consumer_mutex);

    end_data = true;

    pthread_cond_signal(&producer_to_consumer_cond);
    pthread_mutex_unlock(&producer_to_consumer_mutex);
}


void consumer_routine_cleanup(void *arg) {
    set_consumer_running(false);
}

void *consumer_routine(void *arg) {
    //make thread ignore all cancel requests
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    auto *sum_data_local = new int(0);

    pthread_cleanup_push(&consumer_routine_cleanup, nullptr);

        set_consumer_running(true);

        while (true) {
            //receive data
            pthread_mutex_lock(&producer_to_consumer_mutex);
            while (!got_data && !end_data) {
                pthread_cond_wait(&producer_to_consumer_cond, &producer_to_consumer_mutex);
            }

            if (end_data) {
                pthread_mutex_unlock(&producer_to_consumer_mutex);
                break;
            }
            if (got_data) {
                (*sum_data_local) += data;
                got_data = false;
            }

            pthread_mutex_unlock(&producer_to_consumer_mutex);


            //send confirmation
            pthread_mutex_lock(&consumer_to_producer_mutex);

            received_data = true;

            pthread_cond_signal(&consumer_to_producer_cond);
            pthread_mutex_unlock(&consumer_to_producer_mutex);
        }

    pthread_cleanup_pop(true);

    //return pointer to result
    return (void *) sum_data_local;
}

void *consumer_interruptor_routine(void *arg) {
    //wait consumer
    wait_consumer();

    while (is_consumer_running()) {
        pthread_cancel(consumer_routine_t);
    }
}

int run_threads() {
    pthread_create(&consumer_routine_t, nullptr, consumer_routine, nullptr);

    pthread_create(&consumer_interruptor_routine_t, nullptr, consumer_interruptor_routine, nullptr);

    pthread_create(&producer_routine_t, nullptr, producer_routine, nullptr);

    int *result_row;
    pthread_join(consumer_routine_t, (void **) &result_row);
    int result = *result_row;
    delete result_row;

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}

