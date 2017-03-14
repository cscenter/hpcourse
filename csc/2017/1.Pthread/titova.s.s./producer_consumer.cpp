#include <pthread.h>
#include <iostream>
#include <fstream>
#include <cstdlib>
#include <vector>
#include <stdio.h>


pthread_mutex_t the_mutex;
pthread_cond_t condc, condp, condcs;

pthread_t producer_thread;
pthread_t consumer_thread;
pthread_t interruptor_thread;

volatile long answer = 0;
volatile int eof = 0;
volatile bool start = false;


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
    
    pthread_mutex_lock(&the_mutex);
    int val = 0;
    while (1) {
        start  = true;
        if ((*(Value*)arg).get() != 0) {
            pthread_cond_wait(&condp, &the_mutex);
        }
        if ((int)scanf("%d", &val) != EOF) {
            (*(Value*)arg).update(val);
            pthread_cond_signal(&condc);
        } else {
            eof = 1;
            pthread_cond_signal(&condc);
            break;
        }
        start = false;
    }
    pthread_mutex_unlock(&the_mutex);
    pthread_exit(0);
}

void* consumer_routine(void* arg) {
    
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    pthread_testcancel();
    pthread_mutex_lock(&the_mutex);
    while (1) {
        if (!eof) {
            if ((*(Value*)arg).get() == 0) {
                pthread_cond_wait(&condc, &the_mutex);
            }
            answer += (*(Value*)arg).get();
            (*(Value*)arg).update(0);
            pthread_cond_signal(&condp);
        } else {
            break;
        }
    }
    pthread_mutex_unlock(&the_mutex);
    pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
    pthread_exit(0);
}

void* consumer_interruptor_routine(void* arg) {
    while(!eof) {
        if (start) {
            pthread_cancel(consumer_thread);
        }
    }
    pthread_exit(0);
}



int run_threads() {
    
    pthread_mutex_init(&the_mutex, NULL);
    pthread_cond_init(&condc, NULL);
    pthread_cond_init(&condp, NULL);
    
    Value *v = new Value();
    
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
    freopen("input.txt", "r", stdin);
    std::cout << run_threads() << std::endl;
    return 0;
}
