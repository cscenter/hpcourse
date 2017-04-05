#include <pthread.h>
#include <iostream>

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

pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_barrier_t start_point;
pthread_t producer_thread, consumer_thread, interrupter_thread;

pthread_cond_t cond;
bool read_flag = false;
bool end_of_data = false;

void* producer_routine(void* arg) {
    pthread_barrier_wait(&start_point);
    Value *value = (Value *) arg;

    int new_value;
    while(!end_of_data) {
        cin >> new_value;
        pthread_mutex_lock(&mutex);
        while(read_flag) {
            pthread_cond_wait(&cond, &mutex);
        }

        value->update(new_value);
        read_flag = true;

        if(cin.peek() == '\n') {
            end_of_data = true;
        }

        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);
    }

    pthread_exit(0);
}

void* consumer_routine(void* arg) {
    pthread_barrier_wait(&start_point);
    pthread_setcancelstate (PTHREAD_CANCEL_DISABLE,  NULL);

    Value *value = (Value *) arg;

    int *counter = new int;
    for (;;) {
        pthread_mutex_lock(&mutex);
        while(!read_flag) {
            pthread_cond_wait(&cond, &mutex);
        }

        *counter += value->get();
        read_flag = false;

        if(end_of_data) {
            pthread_mutex_unlock(&mutex);
            pthread_exit(counter);
        }

        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);
    }
}

void* consumer_interruptor_routine(void* arg) {
    pthread_barrier_wait(&start_point);

    while (!end_of_data) {
        pthread_cancel(consumer_thread);
    }
}

int run_threads() {
    Value * value = new Value;
    // Init cond
    pthread_cond_init(&cond, NULL);

    // Init barrier
    pthread_barrier_init(&start_point, NULL, 3);

    // Init threads
    pthread_create(&producer_thread, NULL, producer_routine, value);
    pthread_create(&consumer_thread, NULL, consumer_routine, value);
    pthread_create(&interrupter_thread, NULL, consumer_interruptor_routine, NULL);

    int *result_ptr;
    pthread_join(consumer_thread, (void **) &result_ptr);
    pthread_join(producer_thread, NULL);
    pthread_join(interrupter_thread, NULL);

    int result = *result_ptr;
    delete result_ptr;
    delete value;

    return result;
}

int main() {
    cout << run_threads() << endl;
    return 0;
}
