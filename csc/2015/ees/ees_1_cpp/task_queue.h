#ifndef TASK_QUEUE_H
#define TASK_QUEUE_H

#include <boost/thread/lock_types.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition.hpp>

#include <queue>

template <class T>
class task_queue
{
public:
    void push(T&& item)
    {
        boost::lock_guard<boost::mutex> guard(mutex_);
        queue_.push(std::forward<T>(item));
        condition_.notify_one();
    }

    T pop()
    {
        boost::unique_lock<boost::mutex> guard(mutex_);

        condition_.wait(guard, [&]{ return !queue_.empty(); });

        T result(std::move(queue_.front()));
        queue_.pop();
        return result;
    }

private:
    boost::mutex mutex_;
    boost::condition_variable condition_;
    std::queue<T> queue_;
};

#endif // TASK_QUEUE_H
