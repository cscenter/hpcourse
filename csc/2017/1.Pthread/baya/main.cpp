#include <pthread.h>
#include <iostream>
#include <vector>

using namespace std;

pthread_mutex_t mutex;
pthread_cond_t consumer_cond, producer_cond;
pthread_t producer_thread;
pthread_t consumer_thread;
pthread_t interruptor_thread;


int end_of_array = 0, sum = 0;
vector<int> N;

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
    int _sig = 0xC11C11C1;
    int _value;
};

void *producer_routine(void *arg) {
    pthread_mutex_lock(&mutex);
    Value *val = (Value *) arg;

    for(auto i : N){
        while (val->get()) {
            pthread_cond_wait(&producer_cond, &mutex);/*wait while consumer reads and changes to null*/
        }
        val->update(i);
        pthread_cond_signal(&consumer_cond);/*inform consumer to begin reading*/
    }

    end_of_array = 1;
    pthread_cond_signal(&consumer_cond);
    pthread_mutex_unlock(&mutex);
    pthread_exit(0);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    pthread_testcancel();

    pthread_mutex_lock(&mutex);
    Value *val = (Value *) arg;

    while (true) {
        if (!end_of_array) {
            while (!val->get()) {
                pthread_cond_wait(&consumer_cond, &mutex);/*wait while producer change consumer's null*/
            }
            sum += val->get();
            val->update(0);
            pthread_cond_signal(&producer_cond);/*inform consumer to begin changing value*/
        } else {
            break;
        }
    }

    pthread_mutex_unlock(&mutex);
    pthread_exit(0);
}

void *consumer_interruptor_routine(void *arg) {
    Value *val = (Value *) arg;

    while (!end_of_array) {
        while (val->get())
            pthread_cancel(consumer_thread);/*try interrupt consumer if value isn't null*/
    }

    pthread_exit(0);
}


int run_threads() {
    pthread_cond_init(&consumer_cond, NULL);
    pthread_cond_init(&producer_cond, NULL);
    pthread_mutex_init(&mutex, NULL);

    N.push_back(1);
    N.push_back(2);
    N.push_back(3);
    N.push_back(4);

    Value *value = new Value();

    pthread_create(&producer_thread, NULL, producer_routine, value);
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, value);
    pthread_create(&consumer_thread, NULL, consumer_routine, value);

    pthread_join(interruptor_thread, NULL);
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, NULL);

    pthread_cond_destroy(&consumer_cond);
    pthread_cond_destroy(&producer_cond);
    pthread_mutex_destroy(&mutex);

    return sum;
}

int main() {
    cout << run_threads() << endl;
    return 0;
}
