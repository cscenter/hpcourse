#include <pthread.h>  
#include <iostream>
#include <vector>

using namespace std;

namespace details {

// Class to provide communication between threads of the production-consumer problem
// Is a singleton
class Thread_communicator {
public:
        Thread_communicator(const Thread_communicator& root) = delete;
        Thread_communicator& operator=(const Thread_communicator&) = delete;
        ~Thread_communicator();

        // Thread_communicator is a singleton - one for all routines
        // As stated [here](https://stackoverflow.com/a/12249115) it is a thead-safe version
        static Thread_communicator& Instance() {
            static Thread_communicator instance;
            return instance;
        }

        // Thread wait for consumer to start
        void wait_for_consumer();

        // Ends any active wait for consumer
        // Supposed to be ran only by consumer function
        void consumer_notify_start();

        // Updates contained value:
        // - Expects locked _value_mutex
        // - Waits for verification that consumer got previous data 
        // (waits for Production to become an active thread), unlocks _value_mutex
        // and locks _value_mutex after verification given
        // - Changes contained value
        // - If there are no data to come, the state is changed at the moment
        // - Sets Consumation as an active thread and releases _value_mutex
        void update(int value, bool new_data_status = true);

        // Gets contained value and sets data_status:
        // - Expects locked _value_mutex
        // - Waits for verification that production sent previous data 
        // (waits for Consumer to become an active thread), unlocks _value_mutex
        // and locks _value_mutex after verification given
        // - Gets contained value
        // - Sets Production as an active thread and releases _value_mutex
        int get_value();

        // Thread-safely returns if there are data left to consume
        bool get_data_status();

        void lock_value();
        void unlock_value();

private:        
        Thread_communicator();

        pthread_mutex_t _consumer_wait_mutex;
        pthread_cond_t _consumer_wait_cond;

        pthread_mutex_t _value_mutex;
        pthread_mutex_t _data_status_mutex;

        enum class Thread_type {
            Production,
            Consumation
        };

        struct Thread_state {
            Thread_state();
            ~Thread_state();
            
            Thread_type active_thread;
            pthread_cond_t cond;

            template <Thread_type thread>
            void wait_untill_active(pthread_mutex_t* mutex_ptr);

            template <Thread_type thread>
            void change_state();

        } _value_thread_state;

        bool _consumer_start = false;
        bool _there_is_data_to_come = true;
        int _value = 0;
};

}


void* producer_routine(void* arg) {
    auto inst = details::Thread_communicator::Instance;

    // Wait for consumer to start
    inst().wait_for_consumer();

    auto input_data = std::vector<int>();
    input_data.reserve(64);

    // Read data
    for (int data; std::cin >> data; input_data.push_back(data));

    // Loop through each value and update the value, notify consumer, wait for consumer to process
    inst().lock_value();

    for (auto data : input_data) {
        inst().update(data);
    }
    // Notify that production has resigned
    inst().update(0, false);

    inst().unlock_value();

    pthread_exit(nullptr);
}

void* consumer_routine(void* arg) {
    // Set cancelation disabled. The tread is unstoppable now
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    auto inst = details::Thread_communicator::Instance;

    // Notify about start
    inst().consumer_notify_start();

    int* sum = new int();

    // Allocate value for result
    // For every update issued by producer, read the value and add to sum
    inst().lock_value();

    while (inst().get_data_status()) {
        *sum += inst().get_value();
    }

    inst().unlock_value();

    // return pointer to result
    pthread_exit(sum);
}

void* consumer_interruptor_routine(void* arg) {
    auto inst = details::Thread_communicator::Instance;

    // Wait for consumer to start
    inst().wait_for_consumer();

    // Get the consumer thread adress
    pthread_t* consumer_thread = (pthread_t*) arg;

    // Interrupt consumer while producer is running
    while (inst().get_data_status()) {
        pthread_cancel(*consumer_thread);
    }

    pthread_exit(nullptr);
}

int run_threads() {
    pthread_t producer_thread, consumer_thread, consumer_interruptor_thread;

    pthread_create(&producer_thread, nullptr, producer_routine, nullptr);
    pthread_create(&consumer_thread, nullptr, consumer_routine, nullptr);
    pthread_create(&consumer_interruptor_thread, nullptr, consumer_interruptor_routine, (void*)&consumer_routine);

    int* res;
    pthread_join(producer_thread, nullptr);
    pthread_join(consumer_thread, (void**)&res);
    pthread_join(consumer_interruptor_thread, nullptr);

    int stack_res = *res;
    delete res;

    return stack_res;
}

int main() {
    std::cout << run_threads() << std::endl;
    return 0;
}


namespace details {

void Thread_communicator::wait_for_consumer() { 
    pthread_mutex_lock(&_consumer_wait_mutex);
    while(!_consumer_start) pthread_cond_wait(&_consumer_wait_cond, &_consumer_wait_mutex);
    pthread_cond_broadcast(&_consumer_wait_cond);
    pthread_mutex_unlock(&_consumer_wait_mutex);
}

void Thread_communicator::consumer_notify_start() {
    pthread_mutex_lock(&_consumer_wait_mutex);
    _consumer_start = true;
    pthread_cond_broadcast(&_consumer_wait_cond);
    pthread_mutex_unlock(&_consumer_wait_mutex);
}

void Thread_communicator::update(int value, bool new_data_status) {
    _value_thread_state.wait_untill_active<Thread_type::Production>(&_value_mutex);

    _value = value;
    if (!new_data_status) {
        pthread_mutex_lock(&_data_status_mutex);
        _there_is_data_to_come = new_data_status;
        pthread_mutex_unlock(&_data_status_mutex);
    }

    _value_thread_state.change_state<Thread_type::Consumation>();
}

int Thread_communicator::get_value() {
    _value_thread_state.wait_untill_active<Thread_type::Consumation>(&_value_mutex);

    int value = _value;

    _value_thread_state.change_state<Thread_type::Production>();
    return value;
}

Thread_communicator::Thread_communicator() {
    pthread_mutex_init(&_value_mutex, nullptr);
    pthread_mutex_init(&_data_status_mutex, nullptr);

    pthread_mutex_init(&_consumer_wait_mutex, nullptr);
    pthread_cond_init(&_consumer_wait_cond, nullptr);
}

Thread_communicator::~Thread_communicator() {
    pthread_mutex_destroy(&_value_mutex);
    pthread_mutex_destroy(&_data_status_mutex);

    pthread_mutex_destroy(&_consumer_wait_mutex);
    pthread_cond_destroy(&_consumer_wait_cond);
}

Thread_communicator::Thread_state::Thread_state() {
    active_thread = Thread_type::Production;
    pthread_cond_init(&cond, nullptr);
}

Thread_communicator::Thread_state::~Thread_state() {
    pthread_cond_destroy(&cond);
}

template <Thread_communicator::Thread_type thread>
void Thread_communicator::Thread_state::wait_untill_active(pthread_mutex_t* mutex_ptr) {
    while(active_thread != thread) pthread_cond_wait(&cond, mutex_ptr);
}

template <Thread_communicator::Thread_type thread>
void Thread_communicator::Thread_state::change_state() {
    active_thread = thread;
    pthread_cond_broadcast(&cond); // As we use one condition, we should notify them all?
}

void Thread_communicator::lock_value() {
    pthread_mutex_lock(&_value_mutex);
}

void Thread_communicator::unlock_value() {
    pthread_mutex_unlock(&_value_mutex);
}

bool Thread_communicator::get_data_status() {
    pthread_mutex_lock(&_data_status_mutex);
    auto res = _there_is_data_to_come;
    pthread_mutex_unlock(&_data_status_mutex);
    return res;
}

}
