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

pthread_mutex_t mutex;
pthread_cond_t cond;
pthread_cond_t cond2;

bool read_data = true;
bool finish = false;
bool consumer = false;

void* producer_routine(void* arg) {

    auto value = (Value*)arg;
    std::vector<int> data;
    int elem;
    while (std::cin >> elem)
        data.push_back(elem);

    while (!data.empty()){
        pthread_mutex_lock(&mutex);

        while(!read_data)
            pthread_cond_wait(&cond, &mutex);

        value->update(data.back());
        data.pop_back();
        read_data = false;

        pthread_cond_signal(&cond);

        pthread_mutex_unlock(&mutex);
    }

        finish = true;
      //  pthread_mutex_lock(&mutex);
        read_data = false;
        pthread_cond_signal(&cond);
       // pthread_mutex_unlock(&mutex);

    pthread_exit(NULL);

}

void* consumer_routine(void* arg) {

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    consumer = true;
    pthread_cond_signal(&cond2);

    auto value = (Value*)arg;
    int *sum = new int(0);

    while (!finish){
        pthread_mutex_lock(&mutex);

        while(read_data)
            pthread_cond_wait(&cond, &mutex);

        *sum += value->get();
        read_data = true;
        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);

    }

    pthread_exit((void *) sum);

}

void* consumer_interruptor_routine(void* arg) {

    pthread_t *thread_cancel = (pthread_t*)arg;

    while (!consumer)
        pthread_cond_wait(&cond2, &mutex);

    while (!finish)
        pthread_cancel(*thread_cancel);

    pthread_exit(NULL);

}

int run_threads() {

    int ret;
    auto value = new Value();

    pthread_t threads[3];
    void *(*function[3])(void*) = {producer_routine, consumer_routine, consumer_interruptor_routine};
    int *results[3];

    pthread_mutex_init(&mutex,0);
    pthread_cond_init(&cond, 0);
    pthread_cond_init(&cond2, 0);


    for (int i = 0; i < 2; ++i) {
        ret = pthread_create(&threads[i], NULL, function[i], (void *) value);
        if(ret != 0) {
            perror("pthread_create failed\n");
            exit(EXIT_FAILURE);
        }
    }

    ret = pthread_create(&threads[2], NULL, function[2], (void*)&threads[1]);
    if(ret != 0) {
        perror("pthread_create failed\n");
        exit(EXIT_FAILURE);
    }

    for(int i = 0; i < 3; i++)
        pthread_join(threads[i], (void **)&results[i]);

    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond);
    pthread_cond_destroy(&cond2);

    int res = *results[1];
    delete results[1];

    return res;
}

int main() {

    std::cout << run_threads() << std::endl;
    return 0;
}
