#include <pthread.h>
#include <unistd.h>
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

    int get() const { return _value; }
    int get_code() const { return _code; }
    void update(int value) { _value = value; }
    void update_code(int code) { _code = code; }
};

bool check_overflow(int sum, int value) {
    return sum > INT32_MAX - value;
}

// params for program;
static size_t consumers_count;
static size_t max_sleep_time;

// params for log
static bool log_status;
static pthread_mutex_t print_mutex = PTHREAD_MUTEX_INITIALIZER;

// params for tsl
pthread_key_t err;
pthread_once_t once = PTHREAD_ONCE_INIT;

// params for sync
pthread_mutex_t mutex;
pthread_cond_t producer_cv;
pthread_cond_t consumer_cv;
State state = work_start;
size_t started_consumers = 0;

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

// get random index in [0, consumers_count)
int random_idx() {
    return std::rand() % (consumers_count);
}

// get random time in [0, max_sleep_time]
timespec random_msec() {
    int msec = ((int)(std::rand() % (max_sleep_time + 1))) * 1000000;
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
    // wait for consumers to start
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

    // loop through each value
    for (auto it = numbers.begin(); it < numbers.end(); ++it)
    {
        pthread_mutex_lock(&mutex);
        while(state != consumer_ready) {
            // wait for consumer to process
            pthread_cond_wait(&consumer_cv, &mutex);
        }
        ((Value*)arg)->update(*it);
        state = producer_ready;
        // notify consumers about producer ready, so signal on producer_cv
        pthread_cond_signal(&producer_cv);
        pthread_mutex_unlock(&mutex);
    }

    while(state != consumer_ready) {}
    // end producer work
    pthread_mutex_lock(&mutex);
    state = work_done;
    pthread_cond_broadcast(&producer_cv);
    pthread_mutex_unlock(&mutex);

    pthread_exit(EXIT_SUCCESS);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    set_last_error(NOERROR);

    // notify about consumer ready so signal on consumer_cv
    pthread_mutex_lock(&mutex);
    started_consumers++;
    if (started_consumers == consumers_count) {
        state = consumer_ready;
        pthread_cond_broadcast(&consumer_cv);
    }
    pthread_cond_wait(&producer_cv, &mutex);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    Value * sum = new Value();

    while (state != work_done)
    {
        pthread_mutex_lock(&mutex);
        // read the value, check overflow and add to sum
        Value * value = static_cast<Value*>(arg);
        if (check_overflow(sum->get(), value->get())) {
            sum->update_code(OVERFLOW);
            set_last_error(OVERFLOW);
        } else {
            sum->update(sum->get() + value->get());
            value->update(0);
        }

        // notify about consumer ready so signal on consumer_cv
        if (state != work_done) { state = consumer_ready; }
        pthread_cond_signal(&consumer_cv);
        pthread_mutex_unlock(&mutex);

        // sleep
        timespec tw = random_msec();
        timespec tr;
        nanosleep(&tw, &tr);
    }
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);

    // return pointer to result
    pthread_exit(sum);
}

void* consumer_interruptor_routine(void* arg) {
    // wait for consumer to start
    pthread_start(work_start, mutex, consumer_cv);

    // interrupt consumer while producer is running
    pthread_t * consumers = static_cast<pthread_t *>(arg);
    while(state != work_done) {
        pthread_cancel(consumers[random_idx()]);
    }
    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    int sum = 0;
    pthread_once(&once, once_creator);
    set_last_error(NOERROR);

    pthread_t producer;
    pthread_t interruptor;
    pthread_t * consumer = new pthread_t[consumers_count];
    Value * v = new Value();
    Value ** result_p = new Value * [consumers_count];

    LOG("begin main threads", 0);

    CHECK_ERROR(pthread_mutex_init(&mutex, NULL), "init Mutex");
    CHECK_ERROR(pthread_cond_init(&producer_cv, NULL), "init Producer conditional");
    CHECK_ERROR(pthread_cond_init(&consumer_cv, NULL), "init Consumer conditional");
    LOG("create conditional for producer and consumers", 0);

    // start 2+N threads
    CHECK_ERROR(pthread_create(&producer, NULL, producer_routine, (void *) v), "create Producer");
    CHECK_ERROR(pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) consumer), "create Interruptor");
    for (size_t i = 0; i < consumers_count; ++i) {
        CHECK_ERROR(pthread_create(&consumer[i], NULL, consumer_routine, (void *) v), "create Consumer " + std::to_string(i));
    }
    LOG("create threads producer, N consumer and interruptor", 0);

    // wait until 2+N threads done
    CHECK_ERROR(pthread_join(producer, NULL), "join Producer");
    CHECK_ERROR(pthread_join(interruptor, NULL), "join Interruptor");
    for (size_t i; i < consumers_count; ++i) {
        CHECK_ERROR(pthread_join(consumer[i], (void**) &result_p[i]), "join Consumer");
    }
    LOG("join threads", 0);

    CHECK_ERROR(pthread_mutex_destroy(&mutex), "destroy Mutex");
    CHECK_ERROR(pthread_cond_destroy(&producer_cv), "destroy Producer");
    CHECK_ERROR(pthread_cond_destroy(&consumer_cv), "destroy Consumer");
    LOG("delete conditional", 0);

    for (size_t i = 0; i < consumers_count; ++i) {
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
    consumers_count = a > 0 ? a : 0;
    max_sleep_time = b > 0 ? b : 0;

    run_log(false);
    return run_threads();
}