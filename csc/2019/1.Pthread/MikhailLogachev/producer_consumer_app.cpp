#include <pthread.h>
#include <limits.h>
#include <iostream>


#define ERR_CODE_NOERROR 0
#define ERR_CODE_OVERFLOW 1


using namespace std;

thread_local int err_code       = ERR_CODE_NOERROR;

pthread_t * consumer_threads;
bool      * consumer_thread_state;
size_t n_threads                = 0;

size_t n_running_consumers;
pthread_mutex_t rt_mutex        = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t thread_running   = PTHREAD_COND_INITIALIZER;

int mt_wait                     = 0;

int current_value;
bool consumed                   = true;
bool done                       = false;
pthread_cond_t  produced_value  = PTHREAD_COND_INITIALIZER;
pthread_cond_t  consumed_value  = PTHREAD_COND_INITIALIZER;
pthread_mutex_t value_mutex     = PTHREAD_MUTEX_INITIALIZER;


int get_last_error() {
    // return per-thread error code
    return err_code;
}


void set_last_error(int code) {
    // set per-thread error code
    err_code = code;
}


void thread_wait_for_others() {
    pthread_mutex_lock(&rt_mutex);
    while (n_running_consumers < n_threads) {
        pthread_cond_wait(&thread_running, &rt_mutex);
    }
    pthread_mutex_unlock(&rt_mutex);
}


bool is_int_overflow(int current, int to_add) {
    bool res = false;

    if (current > 0 && to_add > INT_MAX - current) res = true; // overflow
    if (current < 0 && to_add < INT_MIN - current) res = true; // underflow

    return res;
}

void random_sleep() {// sleeping random amount of time
    if (mt_wait == 0) {
        timespec zero_spec {};
        zero_spec.tv_nsec = 0;
        zero_spec.tv_sec  = 0;

        nanosleep(&zero_spec, &zero_spec);
        return;
    }

    int sleep_tm = rand() / ((RAND_MAX) / mt_wait);
    timespec tspec{};
    tspec.tv_nsec = sleep_tm * 1000;
    tspec.tv_sec  = sleep_tm / 1000;

    timespec rem = tspec;
    nanosleep(&tspec, &rem);
}


void *producer_routine(void *arg) {
    thread_wait_for_others();

    pthread_mutex_lock(&value_mutex);

    int reading;

    while (cin >> reading) {
        current_value = reading;
        consumed = false;

        pthread_cond_signal(&produced_value);

        while (!consumed) {
            pthread_cond_wait(&consumed_value, &value_mutex);
        }
    }
    current_value = 0;
    done = true;

    pthread_cond_broadcast(&produced_value);
    pthread_mutex_unlock(&value_mutex);

    return nullptr;
}

void *consumer_routine(void *arg) {

    // avoid cancelling ourselves
    int status = pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    // not much we can do if pthread_func fails
    if (status != 0) exit(1);

    auto thread_id = reinterpret_cast<long>(arg);

    pthread_mutex_lock(&rt_mutex);
    ++n_running_consumers;
    pthread_cond_broadcast(&thread_running);
    pthread_mutex_unlock(&rt_mutex);

    consumer_thread_state[thread_id] = true;
    thread_wait_for_others();

    int val = 0;
    int local;

    while (true) {
        pthread_mutex_lock(&value_mutex);

        while (consumed && !done) {
            pthread_cond_wait(&produced_value, &value_mutex);
        }

        if (done) {
            pthread_mutex_unlock(&value_mutex);
            break;
        }

        if (!consumed) {
            local = current_value;
            consumed = true;
            pthread_cond_broadcast(&consumed_value);
            pthread_mutex_unlock(&value_mutex);

            // check overflow
            if (is_int_overflow(val, local)) {
                cerr << "Overflow error !" << endl;
                set_last_error(ERR_CODE_OVERFLOW);
                // setting OVERFLOW and exit
                break;
            }

            val += local;
            random_sleep();
        }
    }

    pthread_mutex_lock(&rt_mutex);
    consumer_thread_state[thread_id] = false;
    --n_running_consumers;
    pthread_mutex_unlock(&rt_mutex);


    int* rval = new int[2];
    rval[0] = get_last_error();
    rval[1] = val;
    return static_cast<void *>(rval);
}


void* consumer_interruptor_routine(void* arg) {

    while (!done) {
        int i = rand() % n_threads;
        if (consumer_thread_state[i])
            pthread_cancel(consumer_threads[i]);
    }

  // interrupt random consumer while producer is running
    return nullptr;
}



int run_threads(int n_consumers, int sleep_time_max) {
    // start N threads and wait until they're done
    // return aggregated sum of values

    n_threads = n_consumers;
    mt_wait = sleep_time_max;


    pthread_t producer_th;
    pthread_create(&producer_th, nullptr, &producer_routine, nullptr);
    pthread_t interruptor_th;
    pthread_create(&interruptor_th, nullptr, &consumer_interruptor_routine, nullptr);

    consumer_threads = new pthread_t [n_threads];
    consumer_thread_state = new bool [n_threads];

    for (size_t i = 0; i < n_threads; ++i) {
        pthread_t p;
        pthread_create(&p, nullptr, &consumer_routine, reinterpret_cast<void*>(i));
        consumer_threads[i] = p;
    }

    pthread_join(producer_th, nullptr);

    int  sum            = 0;
    bool overflow_flag  = false;

    for (size_t i = 0; i < n_threads; ++i) {
        pthread_t p = consumer_threads[i];
        void* ret_val;
        pthread_join(p, &ret_val);

        int* res = static_cast<int*>(ret_val);
        int local_err_code = res[0];
        int value = res[1];

        if (!overflow_flag) {
            if (local_err_code == ERR_CODE_OVERFLOW) {
                cerr << "ERR_CODE_OVERFLOW in th #" << (i + 1) << endl;
                overflow_flag = true;
            } else {
                if (is_int_overflow(sum, value)) {
                    overflow_flag = true;
                    continue;
                }
                sum  += value;
            }
        }

        delete[] res;
    }

    if (overflow_flag) {
        cout << "overflow" << endl;
    } else {
        cout << sum << endl;
    }

    delete[] consumer_threads;
    return overflow_flag ? 1 : 0;
}

int main(int argc, char* argv[]) {
    if (argc != 3) {
        cout << "Usage: ./producer_consumer_app 'number_of_consumers' 'consumer_sleep_time'";
        return 1;
    }
    int n_consumers = stoi(argv[1]);
    int sleep_time_max = stoi(argv[2]);
    return run_threads(n_consumers, sleep_time_max);
}


