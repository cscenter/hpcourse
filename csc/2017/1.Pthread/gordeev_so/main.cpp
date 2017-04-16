#include <pthread.h>
#include <iostream>
#include <vector>
#include <sstream>


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

pthread_t threads[3];
pthread_mutex_t value_mutex;
pthread_cond_t cond_producer;
pthread_cond_t cond_consumer;
bool producer_finished{false};
bool consumer_finished{false};
bool updated{true};


void* producer_routine(void* arg) {

    Value * p_val = reinterpret_cast<Value*>(arg);

    long long read_val{};
    std::string str;
    std::vector<long long> buf;

    getline(std::cin, str);
    std::istringstream sstr(str);

    while(sstr >> read_val)
        buf.push_back(read_val);


    for(auto it: buf)
    {
        pthread_mutex_lock(&value_mutex);

        while(updated == false)
            pthread_cond_wait(&cond_producer, &value_mutex);

        p_val->update(it);
        updated = false;

        pthread_cond_signal(&cond_consumer);
        pthread_mutex_unlock(&value_mutex);
    }

    producer_finished = true;
    pthread_cond_signal(&cond_consumer);
}

void* consumer_routine(void* arg)
{
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    Value * p_val = reinterpret_cast<Value*>(arg);
    long long sum{};

    while (!producer_finished)
    {
        pthread_mutex_lock(&value_mutex);

        while(updated == true)
            pthread_cond_wait(&cond_consumer, &value_mutex);

        sum += p_val->get();
        updated = true;

        pthread_cond_signal(&cond_producer);
        pthread_mutex_unlock(&value_mutex);
    }

    consumer_finished = true;
    return reinterpret_cast<void*>(sum);
}

void* consumer_interruptor_routine(void* arg)
{
    while(!consumer_finished)
    {
        pthread_mutex_lock(&value_mutex);
        pthread_cancel(threads[1]);
        pthread_mutex_unlock(&value_mutex);
    }
}

int run_threads() {

    int result{0};

    pthread_attr_t attr;
    Value val{};

    pthread_mutex_init(&value_mutex, NULL);
    pthread_cond_init (&cond_producer, NULL);
    pthread_cond_init (&cond_consumer, NULL);
    
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&threads[0], &attr, producer_routine, (void *)&val);
    pthread_create(&threads[1], &attr, consumer_routine, (void *)&val);
    pthread_create(&threads[2], &attr, consumer_interruptor_routine, NULL);

    pthread_join(threads[0], NULL);
    pthread_join(threads[1], (void**)&result);
    pthread_join(threads[2], NULL);


    pthread_attr_destroy(&attr);
    pthread_mutex_destroy(&value_mutex);
    pthread_cond_destroy(&cond_producer);

    return result;
}


int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}

