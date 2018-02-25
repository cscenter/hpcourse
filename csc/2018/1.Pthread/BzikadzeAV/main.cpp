#include <pthread.h>  
#include <iostream>
#include <vector>

namespace details {

// Class to provide communication between threads of the production-consumer problem
// Is a singleton
class Thread_communicator {
public:
        Thread_communicator(const Thread_communicator& root) = delete;
        Thread_communicator& operator=(const Thread_communicator&) = delete;

        // Thread_communicator is a singleton - one for all routines
        // As stated [here](https://stackoverflow.com/a/12249115) it is a thead-safe version
        static Thread_communicator& Instance() {
            static Thread_communicator instance;
            return instance;
        }

        // Active wait for consumer to start
        // It is supposed that consumer will start pretty fast
        void wait_for_consumer() const;

        // Ends any active wait for consumer
        // Supposed to be ran only by consumer function
        void consumer_notify_start();

        // Updates contained value:
        // - Locks _value_mutex
        // - Waits for verification that consumer got previous data 
        // (waits for Production to become an active thread)
        // - Changes contained value
        // - If there are no data to come, the state is changed at the moment
        // - Sets Consumation as an active thread
        // - _value_mutex is unlocked
        void update(int value, bool new_data_status = true);

        // Gets contained value:
        // - Locks _value_mutex
        // - Waits for verification that production sent previous data 
        // (waits for Consumer to become an active thread)
        // - Gets contained value
        // - Sets Production as an active thread
        // - _value_mutex is unlocked
        int get_value();

        // Returns if there are data left to consume
        bool get_data_status() const;

private:        
        Thread_communicator();

        pthread_mutex_t _value_mutex;

        enum class Thread_type {
            Production,
            Consumation
        };

        struct Thread_state {
            Thread_state();
            
            Thread_type active_thread;
            pthread_cond_t cond;

            template <Thread_type thread>
            void wait_untill_active(pthread_mutex_t* mutex_ptr);

            template <Thread_type thread>
            void change_state();

        } _current_thread_state;

        bool _consumer_start = false;
        bool _there_is_data_to_come = true;
        int _value;
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
    for (auto data : input_data) {
        inst().update(data);
    }

    // Notify that production has resigned
    inst().update(0, false);

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
    while (inst().get_data_status()) {
        *sum += inst().get_value();
    }

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

void Thread_communicator::wait_for_consumer() const { 
    while(!_consumer_start); 
}

void Thread_communicator::consumer_notify_start() {
    _consumer_start = true;
}

void Thread_communicator::update(int value, bool new_data_status) {
    pthread_mutex_lock(&_value_mutex);
    _current_thread_state.wait_untill_active<Thread_type::Production>(&_value_mutex);

    _value = value;
    _there_is_data_to_come = new_data_status;

    _current_thread_state.change_state<Thread_type::Consumation>();
    pthread_mutex_unlock(&_value_mutex);
}

int Thread_communicator::get_value() {
    pthread_mutex_lock(&_value_mutex);
    _current_thread_state.wait_untill_active<Thread_type::Consumation>(&_value_mutex);

    int value = _value;

    _current_thread_state.change_state<Thread_type::Production>();
    pthread_mutex_unlock(&_value_mutex);

    return value;
}

bool Thread_communicator::get_data_status() const {
    return _there_is_data_to_come;
}

Thread_communicator::Thread_communicator() {
    pthread_mutex_init(&_value_mutex, nullptr);
}

Thread_communicator::Thread_state::Thread_state() {
    active_thread = Thread_type::Production;
    pthread_cond_init(&cond, nullptr);
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

}
