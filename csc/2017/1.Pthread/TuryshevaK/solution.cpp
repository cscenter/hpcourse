#include <pthread.h>
#include <iostream>


pthread_mutex_t mutex;

pthread_t producer, consumer, interruptor;

bool producer_finished = false;
bool consumer_start = false;
bool value_updated_by_producer = true;

pthread_cond_t consumer_started_condition;
pthread_cond_t value_updated_by_producer_condition;
pthread_cond_t value_updated_by_consumer_condition;


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


void *producer_routine(void *arg) {
    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int data;
    while (std::cin.peek() != '\n') { //Честно подсмотрено, потому что иначе нифига не работало почему-то :(
        pthread_mutex_lock(&mutex);

        while (!value_updated_by_producer)
            pthread_cond_wait(&value_updated_by_consumer_condition, &mutex);
        std::cin >> data;

        ((Value *) arg)->update(data);
        value_updated_by_producer = false;
        pthread_cond_signal(&value_updated_by_producer_condition);

        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    producer_finished = true;
    pthread_cond_signal(&value_updated_by_producer_condition);
    pthread_mutex_unlock(&mutex);

    return 0;
}

void *consumer_routine(void *arg) {
    // notify about start
    // allocate value for result
    // for every update issued by producer, read the value and add to sum
    // return pointer to result


//    Защита от inrtrruptor'a
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

//    сообщаем о старте
    pthread_mutex_lock(&mutex);
    consumer_start = true;
    pthread_cond_signal(&consumer_started_condition);
    pthread_mutex_unlock(&mutex);

    //   allocate value for result
    long sum = 0;

//    for every update issued by producer, read the value and add to sum
    while (true) {
        pthread_mutex_lock(&mutex);
        if (producer_finished){
            pthread_mutex_unlock(&mutex);
            break;
        }
        while (value_updated_by_producer and !producer_finished) {
            pthread_cond_wait(&value_updated_by_producer_condition, &mutex);

        }
        sum += ((Value *) arg)->get();

        value_updated_by_producer = true;
        pthread_cond_signal(&value_updated_by_consumer_condition);
        pthread_mutex_unlock(&mutex);

    }
    pthread_mutex_lock(&mutex);
    consumer_start = false;
    pthread_mutex_unlock(&mutex);
    
// return result
    return (void *) sum;
}

void *consumer_interruptor_routine(void *arg) {
    pthread_mutex_lock(&mutex);

    // Ждуны
    while (!consumer_start) {
        pthread_cond_wait(&consumer_started_condition, &mutex);

    }
    pthread_mutex_unlock(&mutex);

    // Попытка остановить consumer поток, пока не завершился producer
    while (!producer_finished) {
        pthread_cancel(consumer);

    }

    return 0;
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    Value data;
    long res = 0;

    pthread_cond_init(&consumer_started_condition, NULL);
    pthread_cond_init(&value_updated_by_producer_condition, NULL);
    pthread_cond_init(&value_updated_by_consumer_condition, NULL);
    pthread_mutex_init(&mutex, NULL);

    pthread_create(&producer, NULL, producer_routine, &data);
    pthread_create(&consumer, NULL, consumer_routine, &data);
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, NULL);

    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &res);
    pthread_join(interruptor, NULL);

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&consumer_started_condition);
    pthread_cond_destroy(&value_updated_by_producer_condition);
    pthread_cond_destroy(&value_updated_by_consumer_condition);

    return res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}