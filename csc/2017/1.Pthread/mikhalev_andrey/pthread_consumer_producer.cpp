#include <iostream>
#include <assert.h>
#include <unistd.h>
#include <vector>

using namespace std;


static pthread_barrier_t barrier;
static pthread_mutex_t mutex;
static pthread_cond_t cond_csm;
static pthread_cond_t cond_pr;
static pthread_attr_t attr;

bool enable_produce = false;
bool producing_ended = false;

class Value {
public:
    Value() : _value(0) {};

    void update(int value) {
        _value = value;
    }

    int get() const {
        return _value;
    };
private:
    int _value;
};

void *producer_routine(void *arg) {
    // Prepare data
    Value *value = (Value *) arg; // cast to Value *
    int number = 0;

    int barrier_status = pthread_barrier_wait(&barrier); // wait for consumer and interruptor
    // we and consumer and interruptor passed barrier
    if (barrier_status == 0 || barrier_status == PTHREAD_BARRIER_SERIAL_THREAD) {
        pthread_mutex_lock(&mutex); // lock mutex
        while (1) {
            while (enable_produce) { // if we prepared something for consumer, than wake up consumer and wait
                pthread_cond_wait(&cond_pr, &mutex); // producer release mutex and wait
            }
            // read number and update it
            if (cin >> number) {
                cout << "Producer: produce " << number << endl;
                value->update(number); // update value
                enable_produce = true; // set flag about updated
                pthread_cond_signal(&cond_csm); // wake up consumer
            } else { // input stream has ended, we can exit
                break;
            }
        }
        pthread_mutex_unlock(&mutex); // unlock mutex

        pthread_mutex_lock(&mutex); // lock mutex
        // we finished producing: change finish-flag, wake up consumer and unlock mutex
        producing_ended = true;
        pthread_cond_signal(&cond_csm);
        pthread_mutex_unlock(&mutex); // unlock mutex
    } else { // error barrier passing
        assert(!barrier_status);
    }
    // exit
    pthread_exit(NULL);
}

void *consumer_routine(void *arg) {
    // Notify about start
    cout << "Consumer start " << endl;

    // Defence from interruptor
    int status = pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    assert(!status);

    // allocate sum
    Value *value = (Value *) arg;
    int sum = 0;

    int barrier_status = pthread_barrier_wait(&barrier); // wait for producer and interruptor
    // we and producer and interruptor passed barrier
    if (barrier_status == 0 || barrier_status == PTHREAD_BARRIER_SERIAL_THREAD) {
        pthread_mutex_lock(&mutex); // lock mutex
        while (1) {
            // if we have nothing to consume and producing hasn't yet ended, than wake up producer and wait
            while (!enable_produce && !producing_ended) {
                pthread_cond_wait(&cond_csm, &mutex); // consumer release mutex and wait
            }
            if (producing_ended) break;
            // consume something
            cout << "Consumer: consumed " << value->get() << endl;
            sum += value->get(); // accumulate sum
            enable_produce = false; // change flag for producer
            pthread_cond_signal(&cond_pr); // wake up producer
        }
        pthread_mutex_unlock(&mutex); // unlock mutex
        // we ended consuming
        pthread_cond_signal(&cond_pr); // wake up producer
    } else { // error barrier passing
        assert(!barrier_status);
    }

    // return result
    Value * tmp = new Value;
    tmp->update(sum);
    pthread_exit(tmp);
}

void *consumer_interruptor_routine(void *arg) {
    int barrier_status = pthread_barrier_wait(&barrier); // wait for consumer and producer
    // we and producer and interruptor passed barrier
    if (barrier_status == 0 || barrier_status == PTHREAD_BARRIER_SERIAL_THREAD) {
        while (!producing_ended) { // try to kill
            pthread_cancel(*(pthread_t *)arg);
        }
    } else { // error barrier passing
        assert(!barrier_status);
    }
    // exit
    pthread_exit(NULL);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    vector<pthread_t> threads(3);
    int result_status = 0;

    // Create barrier
    result_status = pthread_barrier_init(&barrier, NULL, 3);
    assert(!result_status);

    // Create mutex
    result_status = pthread_mutex_init(&mutex, NULL);
    assert(!result_status);

    // Create conditions
    result_status = pthread_cond_init(&cond_csm, NULL); /* Initialize consumer condition variable */
    assert(!result_status);
    result_status = pthread_cond_init(&cond_pr, NULL); /* Initialize producer condition variable */
    assert(!result_status);

    // Create common Value-object
    Value value;

    // Create threads attributes for joinable
    result_status = pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    assert(!result_status);

    // Create Producer
    result_status = pthread_create(&threads[0], NULL, producer_routine, static_cast<void *>(&value));
    assert(!result_status);

    // Create Consumer
    result_status = pthread_create(&threads[1], NULL, consumer_routine, static_cast<void *>(&value));
    assert(!result_status);

    // Create Interruptor
    result_status = pthread_create(&threads[2], NULL, consumer_interruptor_routine, static_cast<void *>(&threads[1]));
    assert(!result_status);

    // Join threads and return SUM
    void *return_value = 0;
    pthread_join(threads[0], NULL);
    pthread_join(threads[1], &return_value);
    pthread_join(threads[2], NULL);

    // save result and free memory
    int result = ((Value *)return_value)->get();
    delete (Value *)return_value;

    // destroy resources
    result_status = pthread_barrier_destroy(&barrier);
    assert(!result_status);
    result_status = pthread_mutex_destroy(&mutex);
    assert(!result_status);
    result_status = pthread_cond_destroy(&cond_csm);
    assert(!result_status);
    result_status = pthread_cond_destroy(&cond_pr);
    assert(!result_status);

    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
