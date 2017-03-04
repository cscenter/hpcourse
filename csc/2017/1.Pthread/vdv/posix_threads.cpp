#include <pthread.h> 
#include <iostream>
#include <vector>
#include <string>

/**
 * Вероятно, можно как-то обойтись без глобалов?
 * Но иначе у меня runtime error
 */
pthread_mutex_t mutex;
pthread_cond_t cond;
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

/**
 * Это вспомогательная функция для получения данных из считанной
 * через getline() строки.
 * Вероятно, я намудрил и есть какой-то способ получше и красивее
 */
void get_data(const std::string& buffer, std::vector<int>& data) {
    std::string tmp = "";

    for (size_t i = 0; i < buffer.size(); ++i)
    {
        if (buffer[i] == ' ') 
        {
            data.push_back(std::stoi(tmp));
            tmp = "";
        } else {
            tmp += buffer[i];
        }
    }

    data.push_back(std::stoi(tmp));
}


void* producer_routine(void* arg) {
    pthread_mutex_lock(&mutex);

    std::string tmp;
    std::vector<int> numbers;
    //---read data
    std::getline(std::cin, tmp);
    get_data(tmp, numbers);

    size_t i = 0;
    while(i < numbers.size())
    {
        if(THREAD_ID == 0)
        {
            (*(Value*)arg).update(numbers[i++]);
            THREAD_ID = 1;
        }
        pthread_cond_signal(&cond);
        pthread_cond_wait(&cond,&mutex);
    }

    THREAD_ID = 1;
    done = true;
    pthread_mutex_unlock(&mutex);
    pthread_cond_signal(&cond);
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
        if(THREAD_ID == 1)
        {
            result += (*(Value*)arg).get();
            THREAD_ID = 2;
        }
        pthread_cond_signal(&cond);
        pthread_cond_wait(&cond,&mutex);
    }

    THREAD_ID = 2;
    pthread_mutex_unlock(&mutex);
    pthread_cond_signal(&cond);

    // return pointer to result
    pthread_exit(&result);
}

void* consumer_interruptor_routine(void* arg) {
    pthread_mutex_lock(&mutex);
    // interrupt consumer while producer is running                                          
    while(!done)
    {
        if(THREAD_ID == 2)
        {
            pthread_cancel(*((pthread_t*)arg));
            THREAD_ID = 0;
        }
        pthread_cond_signal(&cond);
        pthread_cond_wait(&cond,&mutex);
    }
    pthread_mutex_unlock(&mutex);

    pthread_cond_signal(&cond);
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
    pthread_cond_init(&cond, NULL);

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
    pthread_cond_destroy(&cond);

    return *(int*)result;
}
