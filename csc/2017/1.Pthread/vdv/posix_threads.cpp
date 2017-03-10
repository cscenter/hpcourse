#include <pthread.h> 
#include <iostream>

pthread_mutex_t mutex;
pthread_cond_t cond_c, cond_p;
unsigned short THREAD_ID = 0;
bool done = 0;

class Value {
public:
    Value() {
        _value = 0;
    }

    void update(const int value) {
        _value = value;
    }

    int get() const {
        return _value;
    }

private:
    int _value;
};

void* producer_routine(void* arg) {
    pthread_mutex_lock(&mutex);

    //---read data
    int buff;
    THREAD_ID = 1;
    while(true)
    {
        if(THREAD_ID == 1)
        {
            if (!(std::cin >> buff))
            {
                break;
            }
            (*(Value*)arg).update(buff);
            THREAD_ID = 2;
        }
        pthread_cond_signal(&cond_c);
        pthread_cond_wait(&cond_p, &mutex);
    }

    THREAD_ID = 2;
    done = true;
    pthread_mutex_unlock(&mutex);
    pthread_cond_signal(&cond_c);
    pthread_exit(NULL);
}

void* consumer_routine(void* arg) {
    // защищаемся от interrupt
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    // allocate value for result
    static int result = 0;

    pthread_mutex_lock(&mutex);
    while(!done)
    {
        if(THREAD_ID == 2)
        {
            result += (*(Value*)arg).get();
            THREAD_ID = 1;
        }
        pthread_cond_signal(&cond_p);
        pthread_cond_wait(&cond_c, &mutex);
    }

    pthread_mutex_unlock(&mutex);
    pthread_cond_signal(&cond_p);

    // return pointer to result
    pthread_exit(&result);
}

void* consumer_interruptor_routine(void* arg) {
    //---ждем
    pthread_mutex_lock(&mutex);
    while(THREAD_ID == 0)
    {
        pthread_cond_wait(&cond_c, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    // interrupt consumer while producer is running                                          
    while(!done)
    {
        pthread_cancel(*((pthread_t*)arg));
    }

    pthread_exit(NULL);
}

int run_threads() {
    // start 3 threads and wait until they're done
    // return sum of update values seen by consumer
    static pthread_t producer_thread;
    static pthread_t consumer_thread;
    static pthread_t interrupter_thread;
    //---атрибуты потока
    static pthread_attr_t attrs;

    static Value val;
    void* result = NULL;

    //---инициализация атрибутов потока
    if (pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_JOINABLE))
    {
        perror("Error is setting attributes");
    }
    //---инициализация мьюекса
    pthread_mutex_init(&mutex, NULL);

    //---инициализация условных переменных
    pthread_cond_init(&cond_c, NULL);
    pthread_cond_init(&cond_p, NULL);

    //---порождение потоков
    //---создание producer'a, consumer'a & interrupter'a:
    if (pthread_create(&producer_thread, &attrs, producer_routine, &val))
    {
        perror("Cannot create producer_thread!");
    }

    if (pthread_create(&consumer_thread, &attrs, consumer_routine, &val))
    {
        perror("Cannot create consumer_thread!");
    }

    if (pthread_create(&interrupter_thread, &attrs, consumer_interruptor_routine, &consumer_thread))
    {
        perror("Cannot create interrupter_thread!");
    }

    //---ожидание завершения порожденных потоков
    pthread_join(producer_thread, NULL);
    pthread_join(consumer_thread, &result);
    pthread_join(interrupter_thread, NULL);
    //---освобождение ресурсов, занимаемых описателем атрибутов
    pthread_attr_destroy(&attrs);
    //---разрушение мьютекса и cond
    pthread_mutex_destroy(&mutex);
    pthread_cond_destroy(&cond_c);
    pthread_cond_destroy(&cond_p);

    return *(int*)result;
}