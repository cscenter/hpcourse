#include <pthread.h>
#include <iostream>


#define ERR_CODE_NOERROR 0
#define ERR_CODE_OVERFLOW 1


using namespace std;

thread_local int err_code       = ERR_CODE_NOERROR;

size_t n_threads                = 0;

size_t n_running_consumers;
pthread_mutex_t rt_mutex        = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t thread_running   = PTHREAD_COND_INITIALIZER;

int mt_wait                     = 0;

int* current_value;
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

struct data_and_len {
    int * data;
    size_t len;
};


void random_sleep() {// sleeping random amount of time
    int sleep_tm = rand() / ((RAND_MAX) / mt_wait);
    timespec tspec{};
    tspec.tv_nsec = sleep_tm * 1000;
    tspec.tv_sec  = sleep_tm / 1000;

    timespec rem = tspec;
    nanosleep(&tspec, &rem);
}


void *producer_routine(void *arg) {
    thread_wait_for_others();

    current_value = new int {};
    auto vals = static_cast<data_and_len*>(arg);

    pthread_mutex_lock(&value_mutex);

    for (int i = 0; i < vals->len; ++i) {
        *current_value = vals->data[i];
        consumed = false;

        pthread_cond_signal(&produced_value);

        while (!consumed) {
            pthread_cond_wait(&consumed_value, &value_mutex);
        }
    }
    *current_value = 0;
    done = true;

    pthread_cond_broadcast(&produced_value);
    pthread_mutex_unlock(&value_mutex);

    return nullptr;
}

void *consumer_routine(void *arg) {
    pthread_mutex_lock(&rt_mutex);
    ++n_running_consumers;
    pthread_cond_broadcast(&thread_running);
    pthread_mutex_unlock(&rt_mutex);

    thread_wait_for_others();

    auto val = new int {0};
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
            local = *current_value;
            consumed = true;
            pthread_cond_broadcast(&consumed_value);
            pthread_mutex_unlock(&value_mutex);

            // check overflow
            if (local > (INT_MAX - *val)) {
                cerr << "Overflow error !" << endl;
                set_last_error(ERR_CODE_OVERFLOW);
                // setting OVERFLOW and exit
                break;
            }

            *val += local;
            random_sleep();
        }
    }

    arg = static_cast<void*>(val);
    cout << *(static_cast<int*>(arg)) << std::endl;

    pthread_mutex_lock(&rt_mutex);
    --n_running_consumers;
    pthread_mutex_unlock(&rt_mutex);

    return static_cast<void *>(new int{ get_last_error() });
}


//void* consumer_interruptor_routine(void* arg) {
//  // wait for consumers to start
//
//  // interrupt random consumer while producer is running
//}

int run_threads(int n_consumers, int sleep_time_max) {
    // start N threads and wait until they're done
    // return aggregated sum of values

    n_threads = n_consumers;
    mt_wait = sleep_time_max;

    int*    data = new int[n_threads * 10];
    size_t  len  = n_threads * 10;
    for (int i = 0; i < len; ++i) {
        data[i] = i + 1;
    }

    auto vals = data_and_len { data, len };

    pthread_t producer_th;
    pthread_create(&producer_th, nullptr, &producer_routine, static_cast<void*>(&vals));

    auto * consumer_threads = new pthread_t  [n_threads];
    auto * ret_vals         = new int        [n_threads];

    for (size_t i = 0; i < n_threads; ++i) {
        pthread_t p;
        pthread_create(&p, nullptr, &consumer_routine, &ret_vals[i]);
        consumer_threads[i] = p;
    }

    pthread_join(producer_th, nullptr);

    int sum = 0;

    for (size_t i = 0; i < n_threads; ++i) {
        pthread_t p = consumer_threads[i];
        void* ret_code;
        pthread_join(p, &ret_code);

        int res = *(static_cast<int*>(ret_code));
        if (res == ERR_CODE_OVERFLOW) {

        } else {
            cout << ret_vals[i] << " ;";
            sum  += ret_vals[i];
        }
    }


    std::cout << std::endl << n_threads << " :::  " << n_running_consumers << std::endl;
    std::cout << sum << std::endl;

    delete[] ret_vals;
    delete[] consumer_threads;
    return 0;
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


