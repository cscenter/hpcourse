#include <pthread.h>
#include <iostream>
#include <vector>

enum State
{
    consumer_not_start,
    consumer_ready,
    producer_ready,
    producer_stop
};

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
pthread_cond_t producer_cv, consumer_cv;
State state = consumer_not_start;

void* producer_routine(void* arg)
{
    // Wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (state == consumer_not_start)
    {
        // wait for consumer, so wait on consumer_cv
        pthread_cond_wait(&consumer_cv, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    // read data
    int n;
    std::vector<int> numbers;
    while(std::cin >> n)
    {
        numbers.push_back(n);
    }

    // loop through each value and update the value
    for (int i = 0; i < numbers.size(); ++i)
    {
        pthread_mutex_lock(&mutex);

        ((Value*)arg)->update(numbers[i]);
        state = producer_ready;
        // notify consumer about producer ready, so signal on producer_cv
        pthread_cond_signal(&producer_cv);
        while(state != consumer_ready)
        {
            // wait for consumer to process
            pthread_cond_wait(&consumer_cv, &mutex);
        }

        pthread_mutex_unlock(&mutex);
    }

    pthread_mutex_lock(&mutex);
    state = producer_stop;
    pthread_cond_signal(&producer_cv);
    pthread_mutex_unlock(&mutex);

    pthread_exit(EXIT_SUCCESS);
}

void* consumer_routine(void* arg)
{
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);

    pthread_mutex_lock(&mutex);
    state = consumer_ready;
    // notify about consumer ready so signal on consumer_cv
    pthread_cond_broadcast(&consumer_cv);
    pthread_mutex_unlock(&mutex);

    // allocate value for result
    int *sum = new int();

    while (true)
    {
        pthread_mutex_lock(&mutex);

        while(state == consumer_ready) {
            // wait for producer here, so wait on producer_cv
            pthread_cond_wait(&producer_cv, &mutex);
        }
        if (state == producer_stop) {
            pthread_mutex_unlock(&mutex);
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
	    // return pointer to result
            pthread_exit((void *) sum);
        } else {
	    // for every update issued by producer, read the value and add to sum
            *sum += ((Value *) arg)->get();
            state = consumer_ready;
            // notify about consumer ready so signal on consumer_cv
            pthread_cond_signal(&consumer_cv);
            pthread_mutex_unlock(&mutex);
        }
    }
}

void* consumer_interruptor_routine(void* arg)
{
    // wait for consumer to start
    pthread_mutex_lock(&mutex);
    while (state == consumer_not_start)
    {
        pthread_cond_wait(&consumer_cv, &mutex);
    }
    pthread_mutex_unlock(&mutex);

    // interrupt consumer while producer is running
    while (!pthread_cancel(*((pthread_t*)arg))) {}

    pthread_exit(EXIT_SUCCESS);
}

int run_threads()
{
    if (pthread_mutex_init(&mutex, NULL) != 0)
    {
        std::cerr << "Mutex was not created!" << std::endl;
        return 0;
    }
    if (pthread_cond_init(&producer_cv, NULL) != 0)
    {
        std::cerr << "Producer conditional variable was not created!" << std::endl;
        return 0;
    }
    if (pthread_cond_init(&consumer_cv, NULL) != 0)
    {
        std::cerr << "Consumer conditional variable was not created!" << std::endl;
        return 0;
    }

    // start 3 threads and wait until they're done
    Value *v = new Value();
    pthread_t producer, consumer, interruptor;
    if (pthread_create(&producer, NULL, producer_routine, (void *) v) != 0)
    {
        std::cerr << "Thread Producer was not created!" << std::endl;
        return 0;
    }
    if (pthread_create(&consumer, NULL, consumer_routine, (void *) v) != 0)
    {
        std::cerr << "Thread Consumer was not created!" << std::endl;
        return 0;
    }
    if (pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &consumer) != 0)
    {
        std::cerr << "Thread Interruptor was not created!" << std::endl;
        return 0;
    }

    int *result_p;
    if (pthread_join(producer, NULL))
    {
        std::cerr << "Thread Producer was not joined!" << std::endl;
        return 0;
    }
    if (pthread_join(consumer, (void**) &result_p))
    {
        std::cerr << "Thread Consumer was not joined!" << std::endl;
        return 0;
    }
    if (pthread_join(interruptor, NULL))
    {
        std::cerr << "Thread Interruptor was not joined!" << std::endl;
        return 0;
    }

    delete v;
    int result = *result_p;
    delete result_p;

    if (pthread_mutex_destroy(&mutex) != 0)
    {
        std::cerr << "Mutex was not destroyed!" << std::endl;
        return 0;
    }
    if (pthread_cond_destroy(&producer_cv) !=0)
    {
        std::cerr << "Producer conditional variable was not destroyed!" << std::endl;
        return 0;
    }
    if (pthread_cond_destroy(&consumer_cv) != 0)
    {
        std::cerr << "Consumer conditional variable was not destroyed!" << std::endl;
        return 0;
    }

    // return sum of update values seen by consumer
    return result;
}

int main()
{
    std::cout << run_threads() << std::endl;
    return 0;
}
