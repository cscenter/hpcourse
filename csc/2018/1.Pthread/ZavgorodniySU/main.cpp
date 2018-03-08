#include <pthread.h>
#include <iostream>


enum AppState {
    AWAITING_FOR_CONSUMER_START,
    PRODUCED,
    CONSUMED,
    END_OF_INPUT
};


class StateController {
public:
    StateController() {
        pthread_mutex_init(&control_mutex, NULL);
        pthread_mutex_init(&data_mutex, NULL);
        pthread_cond_init(&produce_condition, NULL);
        pthread_cond_init(&consume_condition, NULL);

        app_state = AWAITING_FOR_CONSUMER_START;
        // std::cout << "[STATE] Starting with AWAITING_FOR_CONSUMER_START" << std::endl;
    }

    ~StateController() {
        pthread_mutex_destroy(&control_mutex);
        pthread_mutex_destroy(&data_mutex);
        pthread_cond_destroy(&produce_condition);
        pthread_cond_destroy(&consume_condition);
    }

    void wait_consumer_start() {
        pthread_mutex_lock(&control_mutex);
        while (app_state == AWAITING_FOR_CONSUMER_START) {
            // consumer broadcast produce_condition after initialization
            pthread_cond_wait(&produce_condition, &control_mutex);
        }
        pthread_mutex_unlock(&control_mutex);
    }

    void wait_produce_condition() {
        pthread_mutex_lock(&control_mutex);
        while (app_state != CONSUMED) {
            pthread_cond_wait(&produce_condition, &control_mutex);
        }
        pthread_cond_signal(&produce_condition);
        pthread_mutex_unlock(&control_mutex);
    }

    void wait_consume_condition() {
        pthread_mutex_lock(&control_mutex);
        while (app_state != PRODUCED && app_state != END_OF_INPUT) {
            pthread_cond_wait(&consume_condition, &control_mutex);
        }
        pthread_mutex_unlock(&control_mutex);
    }

    bool is_end_of_input() {
        // It's terminal state
        return app_state == END_OF_INPUT;
    }

    void lock_data() {
        // std::cout << "[STATE] Locking data" << std::endl;
        pthread_mutex_lock(&data_mutex);
    }

    void unlock_data() {
        // std::cout << "[STATE] Unlocking data" << std::endl;
        pthread_mutex_unlock(&data_mutex);
    }

    void notify_consumer() {
        pthread_mutex_lock(&control_mutex);
        app_state = PRODUCED;
        // std::cout << "[STATE] Switch to PRODUCED" << std::endl;
        pthread_cond_broadcast(&consume_condition);
        pthread_mutex_unlock(&control_mutex);
    }

    void notify_producer() {
        pthread_mutex_lock(&control_mutex);
        app_state = CONSUMED;
        // std::cout << "[STATE] Switch to CONSUMED" << std::endl;
        pthread_cond_broadcast(&produce_condition);
        pthread_mutex_unlock(&control_mutex);
    }

    void notify_stop() {
        pthread_mutex_lock(&control_mutex);
        app_state = END_OF_INPUT;
        // std::cout << "[STATE] Switch to END_OF_INPUT" << std::endl;
        pthread_cond_broadcast(&consume_condition);
        pthread_mutex_unlock(&control_mutex);
    }

private:
    AppState app_state;
    pthread_mutex_t control_mutex;
    pthread_mutex_t data_mutex;  // Guard for data mutations
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
    // std::cout << "[PROD] Waiting for consumer" << std::endl;
    params->state_controller->wait_consumer_start();
    // std::cout << "[PROD] Start producing" << std::endl;
    long n;
    while (std::cin >> n) {
        params->state_controller->lock_data();
        *(params->data) = n;
        // std::cout << "[PROD] Produced: " << n << std::endl;
        params->state_controller->unlock_data();
        params->state_controller->notify_consumer();
        params->state_controller->wait_produce_condition();
        // std::cout << "[PROD] Producing next value" << std::endl;
    }

    // std::cout << "[PROD] Turning off producer" << std::endl;
    params->state_controller->notify_stop();
    pthread_exit(EXIT_SUCCESS);
}

void *consumer_routine(void *arg) {
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, NULL);
    RoutineParams *params = (RoutineParams *) arg;
    // std::cout << "[CONS] Start consumer" << std::endl;
    params->state_controller->notify_producer();

    long *sum = new long();

    while (true) {
        params->state_controller->wait_consume_condition();
        if (params->state_controller->is_end_of_input()) {
            // std::cout << "[CONS] Turning off consumer " << std::endl;
            pthread_setcancelstate(PTHREAD_CANCEL_ENABLE, NULL);
            // return result
            pthread_exit((void *) sum);
        } else {
            params->state_controller->lock_data();
            *sum += *(params->data);
            // std::cout << "[CONS] Sum: " << *sum << std::endl;
            params->state_controller->unlock_data();
            params->state_controller->notify_producer();
        }
    }
}

void *consumer_interruptor_routine(void *arg) {
    InterceptorParams *params = (InterceptorParams *) arg;

    // std::cout << "[INT] Waiting for consumer" << std::endl;
    params->state_controller->wait_consumer_start();
    // std::cout << "[INT] Start interrupting" << std::endl;

    while (!pthread_cancel(*(params->producer_thread)) && !params->state_controller->is_end_of_input()) {
        // Just repeat
    }

    // std::cout << "[INT] Turning off interruptor" << std::endl;
    pthread_exit(EXIT_SUCCESS);
}

long run_threads() {
    StateController *ctrl = new StateController();
    volatile long *data = new long(0);
    struct RoutineParams routine_params = {ctrl, data};

    pthread_t producer, consumer, interruptor;
    // std::cout << "[RUNNER] Create producer" << std::endl;
    pthread_create(&producer, NULL, producer_routine, (void *) &routine_params);
    // std::cout << "[RUNNER] Create consumer" << std::endl;
    pthread_create(&consumer, NULL, consumer_routine, (void *) &routine_params);

    struct InterceptorParams interceptor_params = {ctrl, &consumer};
    // std::cout << "[RUNNER] Create interceptor" << std::endl;
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
