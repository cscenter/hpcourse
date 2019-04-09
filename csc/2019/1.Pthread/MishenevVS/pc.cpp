#include <iostream>
#include <limits.h>
#include <cstdlib> // for rand
#include <time.h> // for nanosleep
#include <vector>
#include <pthread.h>


#define NOERROR 0
#define OVERFLOW 1
#define GENERAL_ERROR 2

pthread_key_t last_error_key;


class my_event
{
public:
    my_event( ) :
        wakeup( false )
    {
        pthread_mutex_init( &mutex, NULL );
        pthread_cond_init( &cond, NULL );
    }

    ~my_event()
    {
        pthread_cond_destroy( &cond );
        pthread_mutex_destroy( &mutex );
    }

    void wait()
    {
        pthread_mutex_lock(&mutex);
        while (!wakeup)
            pthread_cond_wait(&cond, &mutex);

        pthread_mutex_unlock(&mutex);
    }

    void trigger()
    {
        pthread_mutex_lock(&mutex);
        wakeup = true;
        pthread_cond_broadcast(&cond);
        pthread_mutex_unlock(&mutex);
        //wakeup = false;
    }

    void reset()
    {
        pthread_mutex_lock( &mutex );
        wakeup = false;
        pthread_mutex_unlock( &mutex );
    }

private:
    pthread_mutex_t mutex;
    pthread_cond_t cond;
    bool wakeup; // to avoid spurious wakeups
};

/*
class my_guard_lock {
public:
  my_guard_lock(pthread_mutex_t &lock) :
	 lock(lock) {
    pthread_mutex_lock(&lock)
  }

  ~my_guard_lock() {
    pthread_mutex_unlock(&lock)
  }
private:
  pthread_mutex_t &lock;
};*/


struct consumer_result
{
    int error_code;
    int partial_sum;
};


my_event consumer_started;
size_t consumer_max_sleep = 0;

bool is_stop_interruptor = false;
bool is_stop_producer = false;
bool is_updated_value = false;
bool is_consumed_data = true;
int active_consumers = 0;

pthread_cond_t produced_happened_cond;
pthread_cond_t consumed_happened_cond;
pthread_mutex_t shared_data_mtx;

bool inline check_overflow(int sum, int d)
{
    return sum > INT32_MAX - d;
}

void tls_destructor(void *value)
{
    delete (int *)value;
    pthread_setspecific(last_error_key, NULL);
}

int get_last_error()
{
    // return per-thread error code
    return *reinterpret_cast<int *>(pthread_getspecific(last_error_key));
    //return reinterpret_cast<int>(pthread_getspecific(last_error_key));
}


void set_last_error(int code)
{
    // set per-thread error code
    int *error_ptr = (int *)pthread_getspecific(last_error_key);
    if(error_ptr == 0)
    {
        error_ptr = new int();
        if(pthread_setspecific(last_error_key, error_ptr))
        {
            std::cerr << " pthread_setspecific is failed" << std::endl;
            pthread_exit((void *)0);
        }
    }
    *error_ptr = code;
    /* if(pthread_setspecific(last_error_key, (void *)code)) {
      std::cerr << " pthread_setspecific is failed" << std::endl;
      pthread_exit((void*)-1);
     }*/
}


void *producer_routine(void *arg)
{
    // wait for consumer to start
    // read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int *shared_data = reinterpret_cast<int *>(arg);
    consumer_started.wait();
    int number = 0;
    while(std::cin >> number)
    {
        pthread_mutex_lock(&shared_data_mtx);
        while(!is_consumed_data)
        {
            pthread_cond_wait(&consumed_happened_cond, &shared_data_mtx);
        }
        is_consumed_data = false;
        *shared_data = number;
        is_updated_value = true;
        // check - is there at least one consumer?
        if(!active_consumers)
        {
            pthread_mutex_unlock(&shared_data_mtx);
            break;
        }
        pthread_cond_signal(&produced_happened_cond);
        pthread_mutex_unlock(&shared_data_mtx);
    }
    pthread_mutex_lock(&shared_data_mtx);
    is_stop_producer = true;
    is_stop_interruptor = true;
    pthread_cond_broadcast(&produced_happened_cond);
    pthread_mutex_unlock(&shared_data_mtx);
    pthread_exit(nullptr);
}


void *consumer_routine(void *arg)
{
    // notify about start
    // for every update issued by producer, read the value and add to sum
    // return pointer to result (for particular consumer)

    int *shared_data = reinterpret_cast<int *>(arg);
    int local_sum = 0, local_copy = 0;
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL); // If a cancellation request is received, it is blocked until cancelability is enabled.
    set_last_error(NOERROR);

    /*[+] process*/
    while(true)
    {
        pthread_mutex_lock(&shared_data_mtx);
        consumer_started.trigger(); // signal to producer and interruptor
        // its a guarantee there is at least one running consumer

        while(!is_updated_value && !is_stop_producer)   // also check the producing end
        {
            pthread_cond_wait(&produced_happened_cond, &shared_data_mtx);
        }

        if(is_updated_value)
        {
            is_consumed_data = true;
            is_updated_value = false;
            local_copy = *shared_data;
        }
        else     // is_stop_producer =true - run out, nothing to do
        {
            pthread_mutex_unlock(&shared_data_mtx);
            active_consumers--; // is protected by shared_data_mtx
            break;
        }
        if (check_overflow(local_sum, local_copy))   //check in mutex else after consumed signal  there is not a gurantee active consumers
        {
            active_consumers--;
            set_last_error(OVERFLOW);
        }

        //[-]
        pthread_cond_signal(&consumed_happened_cond); // or pthread_cond_broadcast(&consumed_happened_cond)
        pthread_mutex_unlock(&shared_data_mtx);

        //[+] working with local copy of data
        if (get_last_error() != NOERROR)
        {
            break;
        }
        else local_sum += local_copy;
        //[-]

        if(consumer_max_sleep)
        {
            int msec = std::rand() % (consumer_max_sleep + 1);
            struct timespec sleeptime = {0, msec * 1000};
            nanosleep(&sleeptime, NULL);
        }
    }

    /*[-] process*/

    consumer_result *result = new consumer_result;
    result->error_code = get_last_error();
    result->partial_sum = local_sum;
    pthread_exit(result);
}


void *consumer_interruptor_routine(void *arg)
{
    // wait for consumers to start
    // interrupt random consumer while producer is running

    consumer_started.wait();
    auto consumers = reinterpret_cast<std::vector<pthread_t> *>(arg);
    while (!is_stop_interruptor)
    {
        size_t rand_ind = std::rand() % consumers->size();
        pthread_t rand_tid = (*consumers)[rand_ind];
        pthread_cancel(rand_tid);
    }

    return nullptr;
}


int run_threads(size_t number_consumers_threads)
{
    // start N threads and wait until they're done
    // return aggregated sum of values

    // [+] initializing
    int shared_data;

    if(number_consumers_threads < 1)
    {
        std::cerr << "Incorrect number of consumers threads" << std::endl;
        return -1;
    }

    if(pthread_key_create(&last_error_key, tls_destructor))
    {
        std::cerr << "Error in pthread_key_create" << std::endl;
        return -1;
    }

    pthread_mutex_init( &shared_data_mtx, NULL );
    pthread_cond_init( &produced_happened_cond, NULL );
    pthread_cond_init( &consumed_happened_cond, NULL );

    // [-] initializing


    /*[+] Creating threads*/

    pthread_t producer_tid;
    pthread_t interr_tid;

    std::vector<pthread_t> consumers(number_consumers_threads);
    pthread_create(&producer_tid, NULL, producer_routine, &shared_data);
    pthread_create(&interr_tid, NULL, consumer_interruptor_routine, &consumers);
    active_consumers = number_consumers_threads;
    for(size_t i = 0; i < number_consumers_threads; ++i)
    {
        if (pthread_create(&consumers[i], NULL, consumer_routine, &shared_data))
        {
            std::cerr << "Error in pthread_create" << std::endl;
            return -1;
        }
    }
    /*[-] Creating threads*/


    /*[+] Waiting producer thread*/
    void *status;
    pthread_join(producer_tid, &status);
    pthread_join(interr_tid, NULL);
    /*[-] Waiting producer threads*/


    /*[+] Aggregating result*/
    int result_code = NOERROR;
    int sum = 0;
    for (size_t i = 0; i < number_consumers_threads; ++i)
    {
        consumer_result *res;
        pthread_join(consumers[i], (void **) &res);
        if(!res)
        {
            //error
            return GENERAL_ERROR;
        }
        if (result_code == NOERROR)
        {
            if (res->error_code != NOERROR)
            {
                result_code = res->error_code;
            }
            else
            {
                if (check_overflow(sum, res->partial_sum))
                    result_code = OVERFLOW;
                else
                    sum += res->partial_sum;
            }
        }
        delete res; // anyway we have to delete results of ALL threads
    }
    /*[-] Aggregating result*/

    if(result_code == NOERROR)
        std::cout <<  sum << std::endl;
    else
        std::cout << "overflow" << std::endl;
    return result_code;
}


int main(int argc, char *argv[])
{
    if(argc != 3)
    {
        std::cerr << "Incorrect number of arguments" << std::endl;
        return -1;
    }
    consumer_max_sleep = atoi(argv[2]);
    return run_threads(atoi(argv[1]));
}
