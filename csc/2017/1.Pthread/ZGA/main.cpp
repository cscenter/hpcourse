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

pthread_cond_t producedCondVar;
pthread_cond_t consumerStartedCondVar;
pthread_cond_t producerLaunchedCondVar;
bool produced = false;
bool allProcessed = false;
bool consumerLaunched = false;
pthread_mutex_t mutex;

void* producer_routine(void* arg) {
    Value *value = static_cast<Value *>(arg);
    std::vector<int> data;
    int a;
    while(std::cin >> a) {
       data.push_back(a);
    }
    for (int i = 0; i < data.size(); ++i) {
        pthread_mutex_lock(&mutex);
        value->update(data[i]);
        produced = true;
        pthread_cond_signal(&producedCondVar);
        pthread_mutex_unlock(&mutex);

        if (i + 1 == data.size()) {
            allProcessed = true;
            pthread_mutex_unlock(&mutex);
            break;
        }

        pthread_mutex_lock(&mutex);
        while(produced) {
            pthread_cond_wait(&producedCondVar, &mutex);
        }
        pthread_mutex_unlock(&mutex);
    }

    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    Value *value = static_cast<Value *>(arg);

    pthread_mutex_lock(&mutex);
    consumerLaunched = true;
    pthread_cond_broadcast(&consumerStartedCondVar);
    pthread_mutex_unlock(&mutex);

    int *res = new int;
    while (true) {
        pthread_mutex_lock(&mutex);
        while (!produced && !allProcessed) {
            pthread_cond_wait(&producedCondVar, &mutex);
        }
        *res += value->get();
        produced = false;
        pthread_cond_signal(&producedCondVar);
        pthread_mutex_unlock(&mutex);

        if (allProcessed) {
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            pthread_exit(res);
        }
    }
}

void* consumer_interruptor_routine(void* arg) {
    pthread_mutex_lock(&mutex);
    while(!consumerLaunched) {
        pthread_cond_wait(&consumerStartedCondVar, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    while (!pthread_cancel(*static_cast<pthread_t *>(arg))) {}
    pthread_exit(NULL);
}

int run_threads() {
    Value value;
    void *res;
    pthread_t producer, consumer, interruptor;
    if (pthread_create(&producer, NULL, producer_routine, (void*) &value)) return 0;
    if (pthread_create(&consumer, NULL, consumer_routine, (void*) &value)) return 0;
    if (pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void*) &consumer)) return 0;
    pthread_join(producer, NULL);
    pthread_join(consumer, &res);
    pthread_join(interruptor, NULL);

    int *res2 = static_cast<int *>(res);
    int ans = *res2;
    delete res2;
    return ans;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}