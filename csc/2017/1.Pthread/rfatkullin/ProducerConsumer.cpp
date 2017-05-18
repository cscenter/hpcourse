#include <pthread.h>  
#include <iostream>
#include <string.h>
#include <sstream>

// Error handling helper macro
#define __FILENAME__ (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : (char*) __FILE__)
#define CheckM(en, msg)\
             do {                                                     \
                int r = (en);                                                \
                if (r != 0) {                                               \
                    std::cerr <<  __FILENAME__ << ": " << __LINE__ << "\t"  \
                        << #en << " " << strerror(r) << "\t" << msg;       \
                    exit(EXIT_FAILURE);                                     \
                }\
            } while (0)
#define Check(en) CheckM(en, "")

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

pthread_mutex_t g_valueMutex;
pthread_cond_t g_valueReadyCV;
bool g_valueReady = false;   // true - producer waits for consumer; false - consumer waits for producer
bool g_queueEnded = false;

void* producer_routine(void* arg) {
    auto value = (Value*)arg;
    // Read data
    std::string line;
    std::getline(std::cin, line);
    std::istringstream input(line);

    while (!input.fail()) {
        // loop through each value
        int v;
        input >> v;

        // and update the value,
        pthread_mutex_lock(&g_valueMutex);

        // Wait for consumer to start or wait for consumer to process
        while (g_valueReady)
            pthread_cond_wait(&g_valueReadyCV, &g_valueMutex);
        g_valueReady = true;

        if (input.fail())
            g_queueEnded = true;
        else
            value->update(v);

        // notify consumer
        pthread_mutex_unlock(&g_valueMutex);
        pthread_cond_signal(&g_valueReadyCV);
    }
    return nullptr;
}

void* consumer_routine(void* arg) {
    auto value = (Value*)arg;

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    // allocate value for result

    auto result = new int{};
    bool queueEnded = false;
    while (!queueEnded) {
        // for every update issued by producer, read the value and add to sum
        pthread_mutex_lock(&g_valueMutex);

        while (!g_valueReady)
            pthread_cond_wait(&g_valueReadyCV, &g_valueMutex);
        g_valueReady = false;

        queueEnded = g_queueEnded;

        if (!queueEnded)
            *result += value->get();

        // notify producer
        pthread_mutex_unlock(&g_valueMutex);
        pthread_cond_signal(&g_valueReadyCV);
    }

    // return pointer to result
    return result;
}

void* consumer_interruptor_routine(void* arg) {

    auto consumer = (pthread_t*)arg;
    // interrupt consumer while producer is running
    while (!g_queueEnded) {
        Check(pthread_cancel(*consumer));
    }
    return nullptr;
}

int run_threads() {
    pthread_t producer;
    pthread_t consumer;
    pthread_t interruptor;
    Value value;

    // start 3 threads and wait until they're done
    Check(pthread_create(&producer, nullptr, &producer_routine, &value));
    Check(pthread_create(&consumer, nullptr, &consumer_routine, &value));
    Check(pthread_create(&interruptor, nullptr, &consumer_interruptor_routine, &consumer));

    void* consumerResult;
    Check(pthread_join(producer, nullptr));
    Check(pthread_join(consumer, &consumerResult));
    Check(pthread_join(interruptor, nullptr));

    int result = *(int*)consumerResult;
    delete (int*)consumerResult;

    // return sum of update values seen by consumer
    return result;
}

int main() {
    Check(pthread_mutex_init(&g_valueMutex, nullptr));
    Check(pthread_cond_init(&g_valueReadyCV, nullptr));

    std::cout << run_threads() << std::endl;
    return 0;
}