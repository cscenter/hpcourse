#include <pthread.h>  
#include <iostream>
#include <vector>

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

// Facilitates thread communication
struct Syncronizer {
    pthread_cond_t *cond;
    pthread_mutex_t *mutex;
    pthread_barrier_t * barrier;
    bool value_accessed = true;
    bool is_last = false;

    Syncronizer() {
        cond = new pthread_cond_t();
        mutex = new pthread_mutex_t();
        barrier = new pthread_barrier_t();
        pthread_mutex_init(mutex, NULL);
        pthread_cond_init(cond, NULL);
        pthread_barrier_init(barrier, NULL, 3);
    }

    ~Syncronizer() {
        pthread_barrier_destroy(barrier);
        pthread_mutex_destroy(mutex);
        pthread_cond_destroy(cond);
        delete barrier;
        delete mutex;
        delete cond;
    }
};

void* producer_routine(void* arg) {
    auto _args = (std::pair<Value *, Syncronizer *> *)arg;
    auto value = _args->first;
    auto sync  = _args->second;
    // Wait for consumer to start
    pthread_barrier_wait(sync->barrier);

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    std::vector<int> data;
    int in;
    while (std::cin >> in) {
        data.push_back(in);
    }
    for (int i = 0; i < data.size(); i++) {
        pthread_mutex_lock(sync->mutex);
        {
            while (!sync->value_accessed) {
                pthread_cond_wait(sync->cond, sync->mutex);
            }
            value->update(data[i]);
            sync->value_accessed = false;
            sync->is_last = i == data.size() - 1;
            pthread_cond_signal(sync->cond);
        }
        pthread_mutex_unlock(sync->mutex);
    }

    // in case of we get no data
    if (data.size() == 0) {
        pthread_mutex_lock(sync->mutex);
        {
            sync->value_accessed = false;
            sync->is_last        = true;
            pthread_cond_signal(sync->cond);
        }
        pthread_mutex_unlock(sync->mutex);
    }

    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    auto _args = (std::pair<Value *, Syncronizer *> *)arg;
    auto value = _args->first;
    auto sync  = _args->second;
    // notify about start
    pthread_barrier_wait(sync->barrier);
    // allocate value for result
    auto result  = new int(0);
    // for every update issued by producer, read the value and add to sum
    bool is_last;
    do {
        pthread_mutex_lock(sync->mutex);
        int current_value = 0;
        {
            while (sync->value_accessed) {
                pthread_cond_wait(sync->cond, sync->mutex);
            }
            current_value = value->get();
            sync->value_accessed = true;
            is_last = sync->is_last;
            pthread_cond_signal(sync->cond);
        }
        pthread_mutex_unlock(sync->mutex);
        *result += current_value;
    } while (!is_last);
    // return pointer to result
    pthread_exit((void *) result);
}

void* consumer_interruptor_routine(void* arg) {
    auto _args  = (std::pair<pthread_t *, Syncronizer *> *)arg;
    auto thread = _args->first;
    auto sync   = _args->second;
    // wait for consumer to start
    pthread_barrier_wait(sync->barrier);

    // interrupt consumer while producer is running
    while (!sync->is_last) {
        pthread_cancel(*thread);
    }

    pthread_exit(NULL);
}

int run_threads() {
    // create arguments for the threads
    auto value   = new Value();
    auto sync    = new Syncronizer();
    auto pc_args = new std::pair<Value *, Syncronizer *>;
    *pc_args     = {value, sync};

    auto interruptor_args = new std::pair<pthread_t *, Syncronizer *>;

    // start 3 threads and wait until they're done
    const auto n_threads = 3;
    pthread_t threads[n_threads];
    int      *results[n_threads];
    pthread_create(&threads[0], NULL, producer_routine, pc_args);
    pthread_create(&threads[1], NULL, consumer_routine, pc_args);

    *interruptor_args = {&threads[1], sync};
    pthread_create(&threads[2], NULL, consumer_interruptor_routine, interruptor_args);

    // join threads
    for (int i = 0; i < n_threads; i++) {
        pthread_join(threads[i], (void **)&results[i]);
    }

    int sum = results[1][0];

    // clean up
    for (int *res : results) {
        if (res != NULL) {
            delete res;
        }
    }
    delete interruptor_args;
    delete pc_args;
    delete sync;
    delete value;

    // return sum of update values seen by consumer
    return sum;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}