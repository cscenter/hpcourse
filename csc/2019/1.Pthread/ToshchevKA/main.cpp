#include <pthread.h>
#include <stdarg.h>
#include <time.h>

#include <string>
#include <cstring>
#include <sstream>
#include <iostream>
#include <vector>

#ifndef MACROS_CHECK_ERROR
#define CHECK_ERROR(cond, message); \
    if(cond != 0) {to_log(1, message, 1);} \
    else {to_log(2, message, 1);}
#endif // MACROS_CHECK_ERROR

#ifndef MACROS_CHECK_VALLIDATION
#define LOG(message, status) to_log(status, message)
#endif // MACROS_CHECK_VALLIDATION

#define NOERROR 0
#define OVERFLOW 1

enum State {
    work_start,
    consumer_ready,
    producer_ready,
    work_done
};

class Value {
    int _value;
    int _code;
public:
    Value() : _value(0), _code(NOERROR) {}

    int get() const {
        return _value;
    }

    int get_code() {
        return _code;
    }

    void update(int value) {
        _value = value;
    }

    void update_code(int code) {
        _code = code;
    }
};

// params for program;
static size_t consumer_threads;
static size_t max_sleep_time;

// params for log
static bool log_status;
static pthread_mutex_t print_mutex = PTHREAD_MUTEX_INITIALIZER;

// params for tsl
pthread_key_t err;
pthread_once_t once = PTHREAD_ONCE_INIT;

pthread_mutex_t mutex;
pthread_cond_t producer_cv;
pthread_cond_t consumer_cv;
State state = work_start;

void to_log(size_t status, std::string message, size_t tabs = 0) {
    if (!log_status) {
        return;
    }

    std::stringstream log_message;
    switch (status) {
    case 0:
        log_message << "[VV]: "; // VV -- validation
        break;
    case 1:
        log_message << "[WW]: "; // WW -- waring
        break;
    case 2:
        log_message << "[II]: "; // II -- information
        break;
    }
    for(size_t i = 0; i < tabs; ++i) {
        log_message << "\t";
    }
    log_message << message;

    pthread_mutex_lock(&print_mutex);
    std::cerr << log_message.str() << std::endl;
    pthread_mutex_unlock(&print_mutex);
}

// return per-thread error code
int get_last_error() {
    return *((int*) pthread_getspecific(err));
}

// set per-thread error code
void set_last_error(const int &code) {
    pthread_setspecific(err, &code);
}

bool check_overflow(int sum, int value) {
  return sum > INT32_MAX - value;
}

// get random index in [0, consumer_threads)
int random_idx() {
    int idx = std::rand() % consumer_threads;
    return idx;
}

// get random time in [0, max_sleep_time)
timespec random_msec() {
    int msec = ((int)(std::rand() % max_sleep_time)) * 1000000;
    return {0, msec};
}

static void once_creator(void) {
    pthread_key_create(&err, NULL);
}

// start thread
void pthread_start(State s, pthread_mutex_t &mutex, pthread_cond_t &cond) {
    pthread_mutex_lock(&mutex);
    if (state == s) {
        pthread_cond_wait(&cond, &mutex);
    }
    pthread_mutex_unlock(&mutex);
}

void* producer_routine(void* arg) {
    // wait for consumer to start
    pthread_start(work_start, mutex, consumer_cv);

    // read data
    pthread_mutex_lock(&mutex);
    std::string str;
    getline(std::cin, str);
    std::stringstream iss(str);
    int n;
    std::vector<int> numbers;
    while(iss >> n) {
        numbers.push_back(n);
    }
    pthread_mutex_unlock(&mutex);
    to_log(0, "[P] end read numbers", 2);

    // loop through each value and update the value
    for (size_t i = 0; i < numbers.size(); ++i)
    {
        pthread_mutex_lock(&mutex);
        if (state == work_done) {
            pthread_mutex_unlock(&mutex);
            break;
        }

        ((Value*)arg)->update(numbers[i]);

        // notify consumer about producer ready, so signal on producer_cv
        state = producer_ready;
        pthread_cond_broadcast(&producer_cv);
        to_log(0, "[P] send broadcast | state " + std::to_string(state), 2);

        while(state != consumer_ready) {
            // wait for consumer to process
            to_log(0, "[P] wait | state " + std::to_string(state), 2);
            pthread_cond_wait(&consumer_cv, &mutex);
        }
        pthread_mutex_unlock(&mutex);
    }

    // end producer work
    to_log(0, "end produser work | state " + std::to_string(state), 2);
    pthread_mutex_lock(&mutex);
    state = work_done;
    pthread_cond_broadcast(&producer_cv);
    pthread_mutex_unlock(&mutex);
    to_log(0, "end end produser work | state " + std::to_string(state), 2);

    pthread_exit(EXIT_SUCCESS);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    set_last_error(NOERROR);
    to_log(0, "[C] enable | state " + std::to_string(state), 2);

    pthread_mutex_lock(&mutex);
    if (state == work_done) {
        pthread_mutex_unlock(&mutex);
        pthread_exit((Value *) new Value());
    } else {
        // notify about consumer ready so signal on consumer_cv
        state = consumer_ready;
        pthread_cond_signal(&consumer_cv);
        pthread_mutex_unlock(&mutex);
    }

    // allocate value for result
    int *sum = new int();

    while (true)
    {
        pthread_mutex_lock(&mutex);

        if (state == work_done) {
            pthread_mutex_unlock(&mutex);
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            break;
        }
        else {
            // wait for producer here, so wait on producer_cv
            while(state == consumer_ready) {
                pthread_cond_wait(&producer_cv, &mutex);
            }
            to_log(0, "[C] start with state " + std::to_string(state), 2);
  
            // read the value, check overflow and add to sum
            int value = ((Value *) arg)->get();
            if (check_overflow(*sum, value)) {
                set_last_error(OVERFLOW);
//                state = producer_stop;
//                to_log(0, "consumer change status" + std::to_string(state) + " : thread " + std::to_string(get_id()), 3);
//
//                pthread_mutex_unlock(&mutex);
//                pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
//                break;
            } else {
                *sum += value;
                to_log(0, "[C] read number " + std::to_string(value) + " | state " + std::to_string(state), 2);
            }

            // notify about consumer ready so signal on consumer_cv
            if (state != work_done) { state = consumer_ready; }
            pthread_cond_signal(&consumer_cv);
            pthread_mutex_unlock(&mutex);

            // sleep
            timespec tw = random_msec();
            timespec tr;
            to_log(0, "[C] sleep in " + std::to_string(tw.tv_nsec / 1000000) + " ms | " + std::to_string(value) + " | state " + std::to_string(state), 2);
            nanosleep(&tw, &tr);
            to_log(0, "[C] wakeup val=" + std::to_string(value) + " | state " + std::to_string(state), 2);
        }
    }
    to_log(0, "[C] prepare for return | status " + std::to_string(state), 3);

    // return pointer to result
    Value * result = new Value();
    result->update(*sum);
    result->update_code(get_last_error());
    to_log(0, "Value: " + std::to_string(result->get()) + ", " + std::to_string(result->get_code()), 3);

    pthread_exit((Value *) result);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_start(work_start, mutex, consumer_cv);
    pthread_t * consumers = (pthread_t *) arg;

    to_log(0, "[I] start", 2);

    // interrupt consumer while producer is running
    pthread_mutex_lock(&mutex);
    while(state != work_done) {
        to_log(0, "[I] try stop consumer by failed", 2);
        pthread_mutex_unlock(&mutex);
        CHECK_ERROR(pthread_cancel(consumers[random_idx()]), "i dont stop cons");
    }
    to_log(0, "[I] end ", 2);
    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    int sum = 0;
    pthread_once(&once, once_creator);
    set_last_error(NOERROR);

    pthread_t producer;
    //pthread_t interruptor;
    pthread_t * consumer = new pthread_t[consumer_threads];
    Value * v = new Value();
    Value ** result_p = new Value * [consumer_threads];

    LOG("begin main threads", 0);

    CHECK_ERROR(pthread_mutex_init(&mutex, NULL), "init Mutex");
    CHECK_ERROR(pthread_cond_init(&producer_cv, NULL), "init Producer conditional");
    CHECK_ERROR(pthread_cond_init(&consumer_cv, NULL), "init Consumer conditional");
    LOG("create conditional for producer and consumers", 0);

    // start 2+N threads
    CHECK_ERROR(pthread_create(&producer, NULL, producer_routine, (void *) v), "create Producer");
    //CHECK_ERROR(pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &consumer), "create Interruptor");
    for (size_t i = 0; i < consumer_threads; ++i) {
        CHECK_ERROR(pthread_create(&consumer[i], NULL, consumer_routine, (void *) v), "create Consumer " + std::to_string(i));
    }
    LOG("create threads producer, N consumer and interruptor", 0);

    // wait until 2+N threads done
    for (size_t i; i < consumer_threads; ++i) {
        CHECK_ERROR(pthread_join(consumer[i], (void**) &result_p[i]), "join Consumer");
    }
    CHECK_ERROR(pthread_join(producer, NULL), "join Producer");
    //CHECK_ERROR(pthread_join(interruptor, NULL), "join Interruptor");
    LOG("join threads", 0);

    CHECK_ERROR(pthread_mutex_destroy(&mutex), "destroy Mutex");
    CHECK_ERROR(pthread_cond_destroy(&producer_cv), "destroy Producer");
    CHECK_ERROR(pthread_cond_destroy(&consumer_cv), "destroy Consumer");
    LOG("delete conditional", 0);

    for (size_t i = 0; i < consumer_threads; ++i) {
        if (get_last_error() == OVERFLOW || result_p[i]->get_code() == OVERFLOW || check_overflow(sum, result_p[i]->get())) {
            set_last_error(OVERFLOW);
        } else {
            sum += result_p[i]->get();
        }
        delete result_p[i];
    }
    delete [] result_p;
    delete v;
    delete consumer;
    LOG("sum values and delete", 0);

    // return aggregated sum of values
    if (get_last_error() == OVERFLOW) {
        std::cout << "overflow" << std::endl;
        return EXIT_FAILURE;
    } else {
        std::cout << sum << std::endl;
        return EXIT_SUCCESS;
    }
}

void run_log(bool enable = false) {
    log_status = enable;
    pthread_mutex_init(&print_mutex, NULL);
}

int main(int argc, char* argv[]) {
    if (argc != 3) {
        std::cerr << "Wrong inputs count args" << std::endl;
        exit(1);
    }

    int a = atoi(argv[1]), b = atoi(argv[2]);
    consumer_threads = a > 0 ? a : 0;
    max_sleep_time = b > 0 ? b : 0;

    run_log(true);
    return run_threads();
}