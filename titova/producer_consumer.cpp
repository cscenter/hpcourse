#include <pthread.h>
#include <iostream>
#include <cstdlib>
#include <vector>
#include <stdio.h>


pthread_mutex_t the_mutex;
pthread_cond_t condc, condp, condend;
pthread_t producer_thread;
pthread_t consumer_thread;
pthread_t interruptor_thread;


int n;
long answer = 0;
std::vector<int> arr;

class Value {
public:
    Value() {
        _value = 0;
    }
    void update(int value) {
        _value = value;
    }
    int get() const {
        return _value;
    }
private:
    int _sig = 0xC11C11C1;
    int _value;
};

void* consumer_routine(void* arg);

void* producer_routine(void* arg) {
    
    for(int i = 0; i < n; i++) {
        pthread_mutex_lock(&the_mutex);
        while ((*(Value*)arg).get()!=0) {
            pthread_cond_wait(&condp, &the_mutex);
        }
        (*(Value*)arg).update(arr[i]);
        pthread_cond_signal(&condc);
        pthread_mutex_unlock(&the_mutex);
    }
    pthread_exit(0);
}

void* consumer_routine(void* arg) {
        for(int i= 0; i < n; i++) {
        int OldState, OldType;
        pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, &OldState);
        pthread_testcancel();
        pthread_mutex_lock(&the_mutex);
        while ((*(Value*)arg).get() == 0) {
            pthread_cond_wait(&condc, &the_mutex);
        }
        answer += (*(Value*)arg).get();
        (*(Value*)arg).update(0);
        pthread_cond_signal(&condp);
        pthread_mutex_unlock(&the_mutex);
        pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, &OldState);
    }
    pthread_exit(0);
}

void* consumer_interruptor_routine(void* arg) {
    for(int i = 0; i < n - 1; i++) {
        while((*(Value*)arg).get() != 0)
            pthread_cancel(consumer_thread);
    }
    pthread_exit(0);
}



int run_threads() {
    
    pthread_mutex_init(&the_mutex, NULL);
    pthread_cond_init(&condc, NULL);
    pthread_cond_init(&condp, NULL);
    
    n  = 5;
    arr.resize(n+1);
    arr[0] = 1;
    arr[1] = 3;
    arr[3] = 3;
    arr[4] = 4;
    arr[2] = 3;
    Value *v = new Value();
    std::cout<< (*v).get() << " start \n";
    

    pthread_create(&producer_thread, NULL, producer_routine, v);
    pthread_create(&interruptor_thread, NULL, consumer_interruptor_routine, v);
    pthread_create(&consumer_thread, NULL, consumer_routine, v);
   
    
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, NULL);
    pthread_join(interruptor_thread, NULL);
    
    pthread_mutex_destroy(&the_mutex);
    pthread_cond_destroy(&condc);
    pthread_cond_destroy(&condp);
    
    return answer;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
