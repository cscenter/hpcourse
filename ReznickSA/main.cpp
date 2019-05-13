#include <pthread.h>

#include <cstdlib>
#include <cstring>
#include <iostream>

#include <unistd.h>

#define NOERROR 0 
#define OVERFLOW 1


pthread_key_t error_key;

int *error_codes;

int get_last_error() {
  return *((int*)pthread_getspecific(error_key));
}

void set_last_error(int code) {
  *((int*)pthread_getspecific(error_key)) = code;
}


bool sum_to_overflow(int a, int b) {
    return b != 0 && ((b > 0) != (a + b > a));
}


struct pc_buffer {
    int v;
    bool stop, arrived;
    pthread_cond_t not_full, not_empty;
    pthread_mutex_t mutex;
    pthread_barrier_t consumers_holder;

    pc_buffer(size_t n_consumers) {
        stop = false;
        arrived = false;
        v = 0;

        pthread_mutex_init(&mutex, nullptr);
        pthread_cond_init(&not_full, nullptr);
        pthread_cond_init(&not_empty, nullptr);

        pthread_barrier_init(&consumers_holder, nullptr, n_consumers + 1);
    }

    ~pc_buffer() {
        pthread_cond_destroy(&not_full);
        pthread_cond_destroy(&not_empty);
        pthread_mutex_destroy(&mutex);
        pthread_barrier_destroy(&consumers_holder);
    }
};

struct producer_params {
    pc_buffer *buffer;
};

struct consumer_params {
    pc_buffer *buffer;
    size_t id;
    int total;
    int error;
    size_t max_sleep;
};

struct interruptor_params {
    pc_buffer *buffer;
    size_t n_consumers;
    pthread_t* t_consumers;
};


void* producer_routine(void *params_ptr) {
    producer_params* for_producer = (producer_params*)params_ptr;
    pc_buffer *buffer = for_producer->buffer;
    int current;

    while (std::cin >> current) {
        pthread_mutex_lock(&buffer->mutex);

        buffer->v = current;
        buffer->arrived = true;
        
        pthread_cond_signal(&buffer->not_empty);


        while (buffer->arrived) {
            pthread_cond_wait(&buffer->not_full, &buffer->mutex);
        }

        pthread_mutex_unlock(&buffer->mutex);
    }

    pthread_mutex_lock(&buffer->mutex);

    buffer->stop = true;

    pthread_cond_broadcast(&buffer->not_empty);

    pthread_mutex_unlock(&buffer->mutex);

    return 0;
}


void* consumer_routine(void *params_ptr) {
    consumer_params* for_consumer = (consumer_params*)params_ptr;
    pc_buffer *buffer = for_consumer->buffer;

    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    pthread_setspecific(error_key, error_codes + for_consumer->id);
    set_last_error(0);

    pthread_barrier_wait(&buffer->consumers_holder);

    int fresh_value, total=0;

    bool once_again = true;
    while (once_again) {
        pthread_mutex_lock(&buffer->mutex);

        while (!buffer->arrived && !buffer->stop){
            pthread_cond_wait(&buffer->not_empty, &buffer->mutex);
        }    

        if (!buffer->stop) {
            fresh_value = buffer->v;
            buffer->arrived = false;
            pthread_cond_signal(&buffer->not_full);

            //bool overflow = fresh_value != 0 && ((fresh_value > 0) != (total + fresh_value > total));

            if (sum_to_overflow(total, fresh_value)) {
	        set_last_error(OVERFLOW);
                once_again = false;
            } else {
	        total += fresh_value;
            }
        } else {
            once_again = false;
        }

        pthread_mutex_unlock(&buffer->mutex);

        usleep(std::rand() % for_consumer->max_sleep);
    }

    for_consumer->error = get_last_error();
    for_consumer->total = total; 
}


void* consumer_interruptor_routine(void* params_ptr) {
    interruptor_params* for_interruptor = (interruptor_params*)params_ptr;
    pc_buffer *buffer = for_interruptor->buffer;

    pthread_barrier_wait(&buffer->consumers_holder);

    bool once_again = true;
    while (once_again) {
        size_t victim_id = std::rand() % for_interruptor->n_consumers;
        pthread_t victim = for_interruptor->t_consumers[victim_id];

        int result = pthread_cancel(victim);

        pthread_mutex_lock(&buffer->mutex);
        once_again = !buffer->stop;
        pthread_mutex_unlock(&buffer->mutex);
    }
}

int run_threads(size_t n_consumers, size_t max_sleep) {
    error_codes = new int[n_consumers];
    for (size_t i = 0; i < n_consumers; i++) {
        error_codes[i] = 0;
    }

    pthread_t t_producer;
    pthread_t *t_consumers = new pthread_t[n_consumers];
    pthread_t t_interruptor;

    pthread_key_create(&error_key, nullptr);
 
    pc_buffer buffer(n_consumers);

    producer_params for_producer = {buffer: &buffer};
    pthread_create(&t_producer, nullptr, producer_routine, &for_producer);

    consumer_params *for_consumers = new consumer_params[n_consumers]; 
    for (size_t i = 0; i < n_consumers; i++) {
        for_consumers[i] = {buffer: &buffer, id: i, total: 0, error: 0, max_sleep: max_sleep};
        pthread_create(t_consumers + i, nullptr, consumer_routine, for_consumers + i);
    }

    interruptor_params for_interruptor = {buffer: &buffer, n_consumers: n_consumers, t_consumers: t_consumers};
    pthread_create(&t_interruptor, nullptr, consumer_interruptor_routine, &for_interruptor);

    pthread_join(t_producer, nullptr);
    pthread_join(t_interruptor, nullptr);

    for (size_t i = 0; i < n_consumers; i++) {
        pthread_join(t_consumers[i], nullptr);
    }

    size_t n_successful = 0;
    int total = 0;
    for (size_t i = 0; i < n_consumers && for_consumers[i].error == 0; i++) {
        if (sum_to_overflow(for_consumers[i].total, total)) {
            break;
        }
        total += for_consumers[i].total;
        n_successful++;
    }


    pthread_key_delete(error_key);

    int code = (n_successful == n_consumers) ? 0 : 1;

    if (code == 0) {
        std::cout << total;
    } else {
        std::cout << "overflow";
    }
    std::cout << std::endl;

    delete[] error_codes;
    delete[] t_consumers;
    delete[] for_consumers;

    return code;
}


int main(int argc, char **argv) {
    if (argc != 3) {
        std::cerr << "usage: " << argv[0] << " <concurrency> <max_timeout>" << std::endl;
        return 2;
    }
    int n_threads = atoi(argv[1]);
    int max_sleep = atoi(argv[2]);
    if (n_threads <= 0) {
        std::cerr << "concurrency must be postive integer" << std::endl;
        return 3;
    }
    if (max_sleep <= 0) {
        std::cerr << "max sleep timeout must be positive integer" << std::endl;
        return 4;
    }

    std::cout << "concurrency " << n_threads << ", max sleep timeout " << max_sleep << "\n";

    return run_threads(n_threads, max_sleep);
}
