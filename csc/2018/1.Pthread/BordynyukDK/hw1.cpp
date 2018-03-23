#include <pthread.h>
#include <iostream>
#include <vector>
#include <iterator>
#include <functional>
#include <cassert>

class Communicator {
    pthread_cond_t consumer_used_value_condition_{};
    pthread_cond_t producer_read_and_update_value_condition_{};
    pthread_mutex_t mutex{};
    pthread_barrier_t barrier{};
    bool value_used_{};
    bool eof_{};
    bool consumer_started_{};
    int value_{};

public:
    Communicator() {
        pthread_mutex_init(&mutex, nullptr);
        pthread_cond_init(&consumer_used_value_condition_, nullptr);
        pthread_cond_init(&producer_read_and_update_value_condition_, nullptr);
        pthread_barrier_init(&barrier, nullptr, 3);
        value_used_ = true;
        eof_ = false;
        value_ = 0;
        consumer_started_ = false;
    }

    ~Communicator() {
        pthread_barrier_destroy(&barrier);
        pthread_mutex_destroy(&mutex);
        pthread_cond_destroy(&producer_read_and_update_value_condition_);
        pthread_cond_destroy(&consumer_used_value_condition_);
    }

    int wait_barrier() {
        return pthread_barrier_wait(&barrier);
    }

    void wait_consumer() {

        //std::cout << "Producer waited that consumer had used value: " << value_ << std::endl;
        while (!value_used_)pthread_cond_wait(&consumer_used_value_condition_, &mutex);
    }

    void wait_producer() {
        //std::cout << "Consumer wait for producer to update value: " << value_ << std::endl;
        while (value_used_) pthread_cond_wait(&producer_read_and_update_value_condition_, &mutex);
    }

    void update(int value) {
        //std::cout << "Shared value update to " << value << " from " << value_ << std::endl;
        value_ = value;
        value_used_ = false;
        pthread_cond_signal(&producer_read_and_update_value_condition_);
    }

    void use(int &sum) {
        sum += value_;
        //std::cout << "Consumer used current value " << value_ << ". Now sum is " << sum << std::endl;
        value_used_ = true;
        pthread_cond_signal(&consumer_used_value_condition_);

    }

    void notify_about_consumer_start() {
        //std::cout << "Consumer start computation" << std::endl;
        consumer_started_ = true;
    }

    void assert_consumer_start() {
        assert(consumer_started_);
    }

    void set_eof(bool eof) {
        eof_ = eof;
    }

    bool check_eof() const {
        return eof_;
    }

    int get_value() {
        return value_;
    }

    template<typename T>
    void sync_block(T block) {
        pthread_mutex_lock(&mutex);
        block();
        pthread_mutex_unlock(&mutex);
    }
};


void *producer_routine(void *arg) {

    auto *communicator = (Communicator *) arg;
    // Wait for consumer to start
    std::vector<int> data{std::istream_iterator<int>{std::cin}, std::istream_iterator<int>{}};
    communicator->wait_barrier();
    communicator->assert_consumer_start();

    // Read data, loop through each value and update the value, notify consumer, wait for consumer to process
    //std::vector<int> data{std::istream_iterator<int>{std::cin}, std::istream_iterator<int>{}};

    for (int &val : data) {

        communicator->sync_block(
                [&]() {
                    communicator->wait_consumer();
                    communicator->update(val);
                    communicator->set_eof(&val == &data.back());

                }
        );
    }

    if (data.empty()) {
        communicator->sync_block(
                [&]() {
                    communicator->update(0);
                    communicator->set_eof(true);
                }
        );
    }

    pthread_exit(EXIT_SUCCESS);

}

void *consumer_routine(void *arg) {
    //can't interrupt
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    auto *extract_args = (std::pair<int *, Communicator *> *) arg;
    // notify about start
    Communicator *communicator = extract_args->second;
    communicator->notify_about_consumer_start();
    communicator->wait_barrier();

    // allocate value for result
    int *sum = extract_args->first;
    *sum = 0;
    // for every update issued by producer, read the value and add to sum
    bool check_eof = false;
    communicator->sync_block([&]() {
                                 while (!check_eof) {
                                     communicator->wait_producer();
                                     communicator->use(*sum);
                                     check_eof = communicator->check_eof();
                                 }
                             }
    );


    // return pointer to result
    pthread_exit(sum);
}

void *consumer_interruptor_routine(void *arg) {
    auto *extract_args = (std::pair<pthread_t *, Communicator *> *) arg;
    pthread_t *consumer_thread = extract_args->first;
    Communicator *communicator = extract_args->second;
    // wait for consumer to start
    communicator->wait_barrier();
    // interrupt consumer while producer is running
    bool check_eof = false;

    while (!check_eof) {
        pthread_cancel(*consumer_thread);
        //std::cout << "Tried to interrupt" << std::endl;
        communicator->sync_block(
                [&]() {
                    check_eof = communicator->check_eof();
                }
        );
    }

    pthread_exit(EXIT_SUCCESS);
}

int run_threads() {
    pthread_t producer_thread, consumer_thread, interruptor_thread;
    Communicator communicator;

    auto *result = new int(0);
    std::pair<int *, Communicator *> consumer_args = std::make_pair(result, &communicator);
    std::pair<pthread_t *, Communicator *> interruptor_args = std::make_pair(&consumer_thread, &communicator);

    // start 3 threads and wait until they're done
    pthread_create(&consumer_thread, nullptr, consumer_routine, &consumer_args);
    pthread_create(&producer_thread, nullptr, producer_routine, &communicator);
    pthread_create(&interruptor_thread, nullptr, consumer_interruptor_routine, &interruptor_args);

    // return sum of update values seen by consumer
    pthread_join(consumer_thread, (void **) &result);
    pthread_join(producer_thread, nullptr);
    pthread_join(interruptor_thread, nullptr);

    int result_value = *result;
    delete result;
    return result_value;

}

int main() {
    std::cout << run_threads() << std::endl;
    //std::cout << "DONE";
    return 0;
}