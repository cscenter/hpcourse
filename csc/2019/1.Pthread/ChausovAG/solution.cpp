#include <pthread.h>
#include <iostream>
#include <vector>
#include <queue>
#include <limits>
#include <unistd.h>
#include <random>
#include <sstream>

#define NOERROR 0
//OVERFLOW is colliding with OVERFLOW defined in <cmath> (required in <random>)
#define OVERFLOW_ERROR 1
#define ZERO_CONSUMERS_ERROR 2

const size_t MESSAGE_QUEUE_MAX_SIZE = 5;
static_assert(MESSAGE_QUEUE_MAX_SIZE != 0, "Message queue size should be > 0");


__thread int tls_last_error = NOERROR;

int get_last_error() {
    return tls_last_error;
}

void set_last_error(int code) {
    tls_last_error = code;
}


class OverflowAwareInt
{
public:
    OverflowAwareInt() = default;
    explicit OverflowAwareInt(int x) : my_int_(x) {}

    OverflowAwareInt& operator+=(int x)
    {
        if (x > 0)
        {
            if (std::numeric_limits<int>::max() - x < my_int_)
            {
                set_last_error(OVERFLOW_ERROR);
                return *this;
            }
        }
        else if (x < 0)
        {
            if (std::numeric_limits<int>::min() - x > my_int_)
            {
                set_last_error(OVERFLOW_ERROR);
                return *this;
            }
        }
        my_int_ += x;
        return *this;
    }

    int get()
    {
        return my_int_;
    }

private:
    int my_int_;
};


class PthreadMutexLock
{
public:
    explicit PthreadMutexLock(pthread_mutex_t* mutex)
            : mutex_(mutex)
    {
        pthread_mutex_lock(mutex_);
    }

    ~PthreadMutexLock()
    {
        pthread_mutex_unlock(mutex_);
    }
private:
    pthread_mutex_t* mutex_;
};

template <typename Message>
class MessageQueue
{
public:
    explicit MessageQueue(size_t max_size)
            : messages_(),
              max_size_(max_size),
              mutex_(PTHREAD_MUTEX_INITIALIZER),
              cond_empty_(PTHREAD_COND_INITIALIZER),
              cond_full_(PTHREAD_COND_INITIALIZER),
              is_closed_(false),
              consumers_count_(0)
    {}

    enum class LinkState { OK, CLOSED };

    LinkState get(Message& message)
    {
        PthreadMutexLock lock{&mutex_};
        while (empty())
        {
            if (is_closed_)
            {
                return LinkState::CLOSED;
            }
            pthread_cond_wait(&cond_empty_, &mutex_);
        }
        message = messages_.front();
        messages_.pop();
        pthread_cond_broadcast(&cond_full_);
        return LinkState::OK;
    }

    template <typename T>
    void put(T&& message)
    {
        PthreadMutexLock lock{&mutex_};
        while (messages_.size() >= max_size_)
        {
            if (consumers_count_ == 0)
            {
                return;
            }
            pthread_cond_wait(&cond_full_, &mutex_);
        }
        messages_.push(std::forward<T>(message));
        pthread_cond_broadcast(&cond_empty_);
    }

    void close()
    {
        PthreadMutexLock lock{&mutex_};
        is_closed_ = true;
        pthread_cond_broadcast(&cond_empty_);
    }

    size_t consumers_count()
    {
        return consumers_count_;
    }

    void add_consumer()
    {
        PthreadMutexLock lock(&mutex_);
        ++consumers_count_;
    }

    void remove_consumer()
    {
        PthreadMutexLock lock(&mutex_);
        if (consumers_count_ == 1)
        {
            pthread_cond_broadcast(&cond_full_);
        }
        --consumers_count_;
    }

    ~MessageQueue()
    {
        pthread_mutex_destroy(&mutex_);
        pthread_cond_destroy(&cond_empty_);
        pthread_cond_destroy(&cond_full_);
    }

private:
    std::queue<Message> messages_;
    const size_t max_size_;
    pthread_mutex_t mutex_;
    pthread_cond_t cond_empty_;
    pthread_cond_t cond_full_;
    bool is_closed_;
    size_t consumers_count_;

    bool empty()
    {
        return messages_.empty();
    }
};

class ConsumerWaiter
{
public:
    explicit ConsumerWaiter(size_t n_consumers) : barrier_()
    {
        pthread_barrier_init(&barrier_, nullptr, static_cast<unsigned int>(n_consumers));
    }

    void wait()
    {
        PthreadMutexLock lock{&mutex_};
        while (!status_)
        {
            pthread_cond_wait(&cond_, &mutex_);
        }
    }

    void notify()
    {
        pthread_barrier_wait(&barrier_);
        PthreadMutexLock lock{&mutex_};
        status_ = true;
        pthread_cond_broadcast(&cond_);
    }

    ~ConsumerWaiter()
    {
        pthread_mutex_destroy(&mutex_);
        pthread_cond_destroy(&cond_);
        pthread_barrier_destroy(&barrier_);
    }

private:
    bool status_ = false;
    pthread_cond_t cond_ = PTHREAD_COND_INITIALIZER;
    pthread_mutex_t mutex_ = PTHREAD_MUTEX_INITIALIZER;
    pthread_barrier_t barrier_;
};

class ProducerFinishChecker
{
public:
    bool is_finished()
    {
        PthreadMutexLock lock{&mutex_};
        auto ret = is_finished_;
        return ret;
    }

    void set_finish()
    {
        PthreadMutexLock lock{&mutex_};
        is_finished_ = true;
        while (!is_read_)
        {
            pthread_cond_wait(&cond_, &mutex_);
        }
    }

    void confirm_read()
    {
        PthreadMutexLock lock{&mutex_};
        is_read_ = true;
        pthread_cond_broadcast(&cond_);
    }

    ~ProducerFinishChecker()
    {
        pthread_cond_destroy(&cond_);
        pthread_mutex_destroy(&mutex_);
    }

private:
    bool is_finished_ = false;
    bool is_read_ = false;
    pthread_cond_t cond_ = PTHREAD_COND_INITIALIZER;
    pthread_mutex_t mutex_ = PTHREAD_MUTEX_INITIALIZER;
};


struct ProducerData
{
    ConsumerWaiter* consumer_waiter;
    MessageQueue<int>* queue;
    ProducerFinishChecker* producer_finish_checker;
};

void* producer_routine(void* arg) {
    auto data = static_cast<ProducerData*>(arg);
    auto link = data->queue;
    auto waiter = data->consumer_waiter;
    auto producer_finish_checker = data->producer_finish_checker;

    // wait for consumer to start
    waiter->wait();

    // read data, loop through each value and update the value, notify consumer, wait for consumer to process
    int number;
    while (std::cin >> number && link->consumers_count() > 0)
    {
        link->put(number);
    }

    producer_finish_checker->set_finish();
    link->close();

    pthread_exit(nullptr);
}


struct ThreadResult
{
    int error_code;
    int result;
};

struct ConsumerData
{
    ConsumerWaiter* consumer_waiter;
    MessageQueue<int>* queue;
    size_t max_sleep_millis;
    ThreadResult out;
};

void* consumer_routine(void* arg) {
    // notify about start
    // for every update issued by producer, read the value and add to sum
    // return pointer to result (for particular consumer)
    pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, nullptr);

    OverflowAwareInt sum{0};
    auto data = static_cast<ConsumerData*>(arg);
    auto link = data->queue;
    auto waiter = data->consumer_waiter;
    auto max_sleep_millis = static_cast<unsigned int>(data->max_sleep_millis);

    std::random_device rd;
    std::mt19937 gen{rd()};
    std::uniform_int_distribution<unsigned int> dis(0, max_sleep_millis);

    link->add_consumer();
    waiter->notify();

    using LinkState = MessageQueue<int>::LinkState;
    LinkState link_state = LinkState::OK;

    while (link_state != LinkState::CLOSED)
    {
        int curr_value = 0;
        link_state = link->get(curr_value);
        if (link_state == LinkState::OK)
        {
            sum += curr_value;
        }
        if (get_last_error() != NOERROR)
        {
            break;
        }
        usleep(dis(gen) * 1000);
    }

    link->remove_consumer();
    data->out = ThreadResult{get_last_error(), sum.get()};
    pthread_exit(nullptr);
}


struct InterrupterData
{
    std::vector<pthread_t>* consumers;
    ConsumerWaiter* waiter;
    ProducerFinishChecker* producer_finish_checker;
};

void* interrupter_routine(void* arg) {
    // wait for consumers to start
    auto data = static_cast<InterrupterData*>(arg);
    auto waiter = data->waiter;
    auto consumers = data->consumers;
    auto producer_finish_checker = data->producer_finish_checker;

    waiter->wait();

    std::random_device rd;
    std::mt19937 gen{rd()};
    std::uniform_int_distribution<size_t> dis(0, consumers->size() - 1);

    // interrupt random consumer while producer is running
    while (!producer_finish_checker->is_finished())
    {
        size_t consumer_idx = dis(gen);
        auto const& thread_to_stop = consumers->at(consumer_idx);
        pthread_cancel(thread_to_stop);
    }

    producer_finish_checker->confirm_read();

    pthread_exit(nullptr);
}


int handle_error(int err_code)
{
    switch (err_code)
    {
        case OVERFLOW_ERROR:
            std::cout << "owerflow" << std::endl;
            break;
        default:
            break;
    }
    return err_code;
}

// start N threads and wait until they're done
// return aggregated sum of values
int run_threads(size_t number_of_consumers_threads, size_t consumer_max_sleep_millis) {
    if (number_of_consumers_threads == 0)
    {
        std::cerr << "number_of_consumers_threads should be > 0" << std::endl;
        return ZERO_CONSUMERS_ERROR;
    }

    MessageQueue<int> link{MESSAGE_QUEUE_MAX_SIZE};
    ConsumerWaiter waiter(number_of_consumers_threads);
    ProducerFinishChecker producer_finish_checker{};

    pthread_t producer_thread;
    ProducerData producer_data{&waiter, &link, &producer_finish_checker};
    pthread_create(&producer_thread, nullptr, producer_routine, static_cast<void*>(&producer_data));

    std::vector<pthread_t> consumer_threads;
    consumer_threads.reserve(number_of_consumers_threads);
    std::vector<ConsumerData> consumer_data_vec;
    consumer_data_vec.reserve(number_of_consumers_threads);
    for (size_t i = 0; i < number_of_consumers_threads; ++i)
    {
        consumer_data_vec.push_back(ConsumerData{&waiter, &link, consumer_max_sleep_millis});
        consumer_threads.emplace_back();
        pthread_create(&consumer_threads.back(), nullptr, consumer_routine,
                       static_cast<void*>(&consumer_data_vec.back()));
    }

    InterrupterData interrupter_data{&consumer_threads, &waiter, &producer_finish_checker};
    pthread_t interrupter_thread;
    pthread_create(&interrupter_thread, nullptr, interrupter_routine, static_cast<void*>(&interrupter_data));

    pthread_join(producer_thread, nullptr);
    pthread_join(interrupter_thread, nullptr);
    for (auto const& thread : consumer_threads)
    {
        pthread_join(thread, nullptr);
    }

    OverflowAwareInt sum{0};
    for (auto const& consumer_data : consumer_data_vec)
    {
        if (consumer_data.out.error_code != NOERROR)
        {
            set_last_error(consumer_data.out.error_code);
            break;
        }
        sum += consumer_data.out.result;
        if (get_last_error() != NOERROR)
        {
            break;
        }
    }

    if (get_last_error() != NOERROR)
    {
        return handle_error(get_last_error());
    }

    std::cout << sum.get() << std::endl;
    return NOERROR;
}

int main(int argc, char** argv) {
    size_t number_of_consumers_threads = std::stoul(std::string(argv[1]));
    size_t consumer_max_sleep_millis = std::stoul(std::string(argv[2]));
    return run_threads(number_of_consumers_threads, consumer_max_sleep_millis);
}
