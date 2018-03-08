#include <pthread.h>
#include <iostream>


enum AppState {
    AWAITING_FOR_CONSUMER_START,
    CONSUMING,
    PRODUCING,
    END_OF_INPUT
};


class StateController {
public:
    StateController() {
        pthread_mutexattr_t mutex_attr;
        pthread_mutexattr_init(&mutex_attr);
        pthread_mutexattr_settype(&mutex_attr, PTHREAD_MUTEX_RECURSIVE);

        pthread_mutex_init(&mutex, &mutex_attr);
        pthread_mutexattr_destroy(&mutex_attr);

        pthread_cond_init(&produce_condition, NULL);
        pthread_cond_init(&consume_condition, NULL);

        app_state = AWAITING_FOR_CONSUMER_START;
    }

    ~StateController() {
        pthread_mutex_destroy(&mutex);
        pthread_cond_destroy(&produce_condition);
        pthread_cond_destroy(&consume_condition);
    }

    void wait_consumer_start() {
        pthread_mutex_lock(&mutex);
        while (app_state == AWAITING_FOR_CONSUMER_START) {
            // consumer broadcast produce_condition after initialization
            pthread_cond_wait(&produce_condition, &mutex);
        }
        pthread_mutex_unlock(&mutex);
    }

    bool is_end_of_input() {
        // It's terminal state
        return app_state == END_OF_INPUT;
    }

    void lock_state(AppState desired_state) {
        pthread_mutex_lock(&mutex);
        pthread_cond_t *condition;

        // Wake condition optimization
        if (desired_state == PRODUCING) {
            condition = &produce_condition;
        } else {
            condition = &consume_condition;
        }

        while (app_state != desired_state && app_state != END_OF_INPUT) {
            pthread_cond_wait(condition, &mutex);
        }
    }

    void unlock_state() {
        pthread_mutex_unlock(&mutex);
    }

    void notify_consumer() {
        pthread_mutex_lock(&mutex);
        app_state = CONSUMING;

        pthread_cond_broadcast(&consume_condition);
        pthread_mutex_unlock(&mutex);
    }

    void notify_producer() {
        pthread_mutex_lock(&mutex);
        app_state = PRODUCING;

        pthread_cond_broadcast(&produce_condition);
        pthread_mutex_unlock(&mutex);
    }

    void notify_stop() {
        pthread_mutex_lock(&mutex);
        app_state = END_OF_INPUT;

        pthread_cond_broadcast(&consume_condition);
        pthread_mutex_unlock(&mutex);
    }

private:
    AppState app_state;
    pthread_mutex_t mutex;
    pthread_cond_t produce_condition, consume_condition;
};


struct RoutineParams {
    StateController *state_controller;
    volatile long *data;
};

struct InterceptorParams {
    StateController *state_controller;
    pthread_t *producer_thread;
};

void *producer_routine(void *arg) {
    RoutineParams *params = (RoutineParams *) arg;

    params->state_controller->wait_consumer_start();

    long n;
    while (std::cin >> n) {
        params->state_controller->lock_state(PRODUCING);
            *(params->data) = n;
            params->state_controller->notify_consumer();
        params->state_controller->unlock_state();
    }

    params->state_controller->lock_state(PRODUCING);  // Guard for last value
        params->state_controller->notify_stop();
    params->state_controller->unlock_state();
    pthread_exit(EXIT_SUCCESS);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    RoutineParams *params = (RoutineParams *) arg;
    long *sum = new long();

    params->state_controller->notify_producer();

    while (true) {
        params->state_controller->lock_state(CONSUMING);
            if (params->state_controller->is_end_of_input()) {
                pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
                // return result
                pthread_exit((void *) sum);
            } else {
                *sum += *(params->data);
                params->state_controller->notify_producer();
            }
        params->state_controller->unlock_state();
    }
}

void *consumer_interruptor_routine(void *arg) {
    InterceptorParams *params = (InterceptorParams *) arg;

    params->state_controller->wait_consumer_start();

    while (!pthread_cancel(*(params->producer_thread)) && !params->state_controller->is_end_of_input()) {
        // Just repeat
    }

    pthread_exit(EXIT_SUCCESS);
}

long run_threads() {
    StateController *ctrl = new StateController();
    volatile long *data = new long(0);
    struct RoutineParams routine_params = {ctrl, data};

    pthread_t producer, consumer, interruptor;

    pthread_create(&producer, NULL, producer_routine, (void *) &routine_params);
    pthread_create(&consumer, NULL, consumer_routine, (void *) &routine_params);

    struct InterceptorParams interceptor_params = {ctrl, &consumer};
    pthread_create(&interruptor, NULL, consumer_interruptor_routine, (void *) &interceptor_params);

    long *result_p;
    pthread_join(producer, NULL);
    pthread_join(consumer, (void **) &result_p);
    pthread_join(interruptor, NULL);

    long result = *result_p;

    delete ctrl;
    delete data;
    delete result_p;
    return result;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}
