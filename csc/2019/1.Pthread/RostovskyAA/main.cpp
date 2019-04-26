#include <pthread.h>
#include <iostream>
#include <random>
#include <unistd.h>
#include <limits.h>

#define NOERROR 0
#define OVERFLOW 1

int data = 0;
int consumersCount = 0;
int sleepLimit = 0;
bool inputFinished = false;
bool elementsToProcess = false;
bool dataReceived = false;
bool consumerLaunched = false;
pthread_mutex_t threadsMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t elementsMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_mutex_t consumerMutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t elementProcessingCondition = PTHREAD_COND_INITIALIZER;
pthread_cond_t condition = PTHREAD_COND_INITIALIZER;
pthread_cond_t isConsumersLaunched = PTHREAD_COND_INITIALIZER;

thread_local int exitCode = NOERROR;

struct output {
    int sum = 0;
    int exitCode = 0;
};

int get_last_error() {
    return exitCode;
}

void set_last_error(int code) {
    exitCode = code;
}

void waitConsumersLaunch() {
    pthread_mutex_lock(&consumerMutex);
    if (!consumerLaunched) {
        pthread_cond_wait(&isConsumersLaunched, &consumerMutex);
    }
    pthread_mutex_unlock(&consumerMutex);
}

void *producer_routine(void *arg) {
    waitConsumersLaunch();

    std::vector<int> arguments;
    int tmp;
    while (std::cin >> tmp) {
        arguments.push_back(tmp);
    }
    elementsToProcess = true;
    for (int argument : arguments) {
        pthread_mutex_lock(&threadsMutex);
        data = argument;
        dataReceived = true;
        pthread_cond_signal(&condition);
        pthread_mutex_unlock(&threadsMutex);

        pthread_mutex_lock(&elementsMutex);
        while (elementsToProcess) {
            pthread_cond_wait(&elementProcessingCondition, &elementsMutex);
        }
        elementsToProcess = true;
        pthread_mutex_unlock(&elementsMutex);
    }
    pthread_mutex_lock(&threadsMutex);
    inputFinished = true;
    pthread_cond_broadcast(&condition);
    pthread_mutex_unlock(&threadsMutex);
    pthread_exit(nullptr);
}

void notifyLaunch() {
    pthread_mutex_lock(&consumerMutex);
    consumerLaunched = true;
    pthread_cond_signal(&isConsumersLaunched);
    pthread_mutex_unlock(&consumerMutex);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);
    notifyLaunch();
    int currentSum = 0;

    while (true) {
        pthread_mutex_lock(&threadsMutex);
        while (!dataReceived && !inputFinished) {
            pthread_cond_wait(&condition, &threadsMutex);
        }

        if (dataReceived) {
            dataReceived = false;
            pthread_mutex_unlock(&threadsMutex);

            pthread_mutex_lock(&elementsMutex);
            elementsToProcess = false;
            pthread_cond_signal(&elementProcessingCondition);
            pthread_mutex_unlock(&elementsMutex);

            if ((data > 0 && data > INT_MAX - data) || (data < 0 && data < INT_MIN - data)) {
                set_last_error(OVERFLOW);
                break;
            }

            currentSum += data;

            usleep(rand() % (sleepLimit + 1) * 1000);
        }

        if (inputFinished) {
            pthread_mutex_unlock(&threadsMutex);
            break;
        }
    }

    auto *out = new output;
    out->sum = currentSum;
    out->exitCode = get_last_error();
    pthread_exit(out);
}

void *consumer_interruptor_routine(void *arg) {
    waitConsumersLaunch();

    while (!inputFinished) {
        pthread_cancel(*static_cast<pthread_t *>(arg));
    }
    pthread_exit(nullptr);
}

int run_threads() {
    int sum = 0;

    pthread_t producer, consumers[consumersCount], interruptor;
    pthread_create(&producer, nullptr, producer_routine, nullptr);
    for (int i = 0; i < consumersCount; ++i) {
        pthread_create(&consumers[i], nullptr, consumer_routine, &data);
    }
    pthread_create(&interruptor, nullptr, consumer_interruptor_routine, &consumers);

    pthread_join(producer, nullptr);
    pthread_join(interruptor, nullptr);
    for (pthread_t consumer : consumers) {
        output *output;
        pthread_join(consumer, (void **) &output);

        if (get_last_error() == OVERFLOW) {
            delete(output);
            continue;
        }

        if (output->exitCode == OVERFLOW
            || ((output->sum > 0 && sum > INT_MAX - output->sum) || (output->sum < 0 && sum < INT_MIN - output->sum))) {
            set_last_error(OVERFLOW);
        }

        sum += output->sum;
        delete (output);
    }

    pthread_mutex_destroy(&threadsMutex);
    pthread_mutex_destroy(&elementsMutex);
    pthread_mutex_destroy(&consumerMutex);

    pthread_cond_destroy(&elementProcessingCondition);
    pthread_cond_destroy(&condition);
    pthread_cond_destroy(&isConsumersLaunched);

    if (get_last_error() == OVERFLOW) {
        std::cout << "overflow" << std::endl;
        return 1;
    }
    std::cout << sum << std::endl;
    return 0;
}

int main(int argc, char *argv[]) {
    consumersCount = atoi(argv[1]);
    sleepLimit = atoi(argv[2]);
    return run_threads();
}