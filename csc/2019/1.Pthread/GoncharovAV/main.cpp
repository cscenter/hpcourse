#include <iostream>
#include <sstream>
#include <pthread.h>
#include <vector>
#include <climits>
#include <memory>
#include <random>
#include <unistd.h>


#define NOERROR 0
#define OVERFLOW 1

using namespace std;

struct ResultT {
    int err_code;
    int sum;
};

bool stop_interruption = false;

struct SharedDataT {

    explicit SharedDataT(int max_time_to_sleep) : wait_reading(false), end_of_input(false), number(0),
                                                  max_time_to_sleep(max_time_to_sleep) {
        pthread_mutex_init(&lock, nullptr);
        pthread_cond_init(&read_cond, nullptr);
        pthread_cond_init(&write_cond, nullptr);
    }

    ~SharedDataT() {
        pthread_mutex_destroy(&lock);
        pthread_cond_destroy(&read_cond);
        pthread_cond_destroy(&write_cond);
    }

    pthread_mutex_t lock;
    pthread_cond_t read_cond;
    pthread_cond_t write_cond;

    bool wait_reading;
    bool end_of_input;
    int number;

    int max_time_to_sleep;
};

pthread_key_t tkey;

void init_error_storage() {
    pthread_setspecific(tkey, nullptr);
}

void clear_error_storage() {
    auto code_p = static_cast<int *>(pthread_getspecific(tkey));
    delete code_p;
}

int get_last_error() {
    auto code_p = static_cast<int *>(pthread_getspecific(tkey));
    return *code_p;
}

void set_last_error(int code) {
    clear_error_storage();

    auto code_p = new int(code);
    pthread_setspecific(tkey, code_p);
}

void exit_consumer(int sum) {
    auto result = new ResultT;
    result->sum = sum;
    result->err_code = get_last_error();
    clear_error_storage();

    pthread_exit(result);
}

stringstream get_input_stream() {
    string input;
    getline(cin, input);
    stringstream input_stream(input);

    return input_stream;
}

void *producer_routine(void *arg) {
    auto data = static_cast<SharedDataT *>(arg);

    int number;
    stringstream input = get_input_stream();
    while (input >> number) {
        pthread_mutex_lock(&data->lock);

        while (data->wait_reading)
            pthread_cond_wait(&data->write_cond, &data->lock);

        data->number = number;

        data->wait_reading = true;
        pthread_cond_broadcast(&data->read_cond);

        pthread_mutex_unlock(&data->lock);
    }
    stop_interruption = true;
    pthread_mutex_lock(&data->lock);

    while (data->wait_reading)
        pthread_cond_wait(&data->write_cond, &data->lock);
    data->end_of_input = true;

    pthread_cond_broadcast(&data->read_cond);
    pthread_mutex_unlock(&data->lock);

    pthread_exit(nullptr);
}


void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    auto data = static_cast<SharedDataT *>(arg);
    init_error_storage();
    set_last_error(NOERROR);

    random_device rd;
    mt19937 rng(rd());
    uniform_int_distribution<int> uni(0, data->max_time_to_sleep);

    int sum = 0;
    while (true) {
        pthread_mutex_lock(&data->lock);

        while (!data->wait_reading && !data->end_of_input)
            pthread_cond_wait(&data->read_cond, &data->lock);

        if (data->end_of_input) {
            pthread_mutex_unlock(&data->lock);
            exit_consumer(sum);
        }

        if (sum > INT_MAX - data->number) {
            pthread_mutex_unlock(&data->lock);
            set_last_error(OVERFLOW);
            exit_consumer(sum);
        }

        sum += data->number;

        data->wait_reading = false;
        pthread_cond_broadcast(&data->write_cond);

        pthread_mutex_unlock(&data->lock);

        int time_to_sleep = uni(rng);
        usleep(time_to_sleep);
    }
}


void *consumer_interruptor_routine(void *arg) {
    auto consumers_ids = static_cast<vector<pthread_t> *>(arg);

    random_device rd;
    mt19937 rng(rd());
    uniform_int_distribution<ulong> uni(0, consumers_ids->size());

    while (!stop_interruption) {
        ulong index = uni(rng);
        pthread_cancel((*consumers_ids)[index]);
    }

    pthread_exit(nullptr);
}


int run_threads(int n_consumers, int max_time_to_sleep) {

    pthread_key_create(&tkey, nullptr);

    vector<pthread_t> consumers_ids;
    pthread_t producer_id;
    pthread_t interruptor_id;

    SharedDataT data(max_time_to_sleep);

    for (size_t i = 0; i < n_consumers; i++) {
        pthread_t tid;
        pthread_create(&tid, nullptr, consumer_routine, &data);
        consumers_ids.push_back(tid);
    }
    pthread_create(&producer_id, nullptr, producer_routine, &data);
    pthread_create(&interruptor_id, nullptr, consumer_interruptor_routine, &consumers_ids);

    int sum = 0;
    for (size_t i = 0; i < n_consumers; i++) {
        void *resultv;
        pthread_join(consumers_ids[i], &resultv);

        unique_ptr<ResultT> result(static_cast<ResultT *>(resultv));
        if (result->err_code == OVERFLOW) {
            stop_interruption = true;
            cout << "overflow" << endl;
            return OVERFLOW;
        }

        sum += result->sum;
    }

    cout << sum << endl;
    return NOERROR;
}


int main(int argc, char *argv[]) {

    if (argc != 3)
        return 1;

    int n_consumers = atoi(argv[1]);
    int max_time_to_sleep = atoi(argv[2]);

    return run_threads(n_consumers, max_time_to_sleep);
}