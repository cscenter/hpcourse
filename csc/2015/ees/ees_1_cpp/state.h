#ifndef STATE_H
#define STATE_H

#include <boost/thread.hpp>
#include <boost/thread/condition.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/locks.hpp>

#include <atomic>
#include <exception>

#include "future.h"
#include "log.h"

template <class R> class future;

enum class TaskState
{
    IDLE,
    RUNNING,
    COMPLETE,
    CANCELLED
};

template <class R> using task_type = std::function<R()>;

class base_state
{
public:
    virtual void do_work(boost::thread* thread_) = 0;

    void wait_for_result();
    void cancel();
    size_t get_id();
    TaskState status();

    void set_exception(std::exception_ptr ptr);
    std::exception_ptr exception() const;

    virtual ~base_state();

protected:
    base_state();

    std::atomic<TaskState> state_;
    boost::condition_variable condition_;
    boost::mutex mutex_;
    std::exception_ptr exception_;
    std::atomic<boost::thread*> thread_;
    size_t id_;
};

template<class R>
class state : public base_state
{
    friend class future<R>;
public:
    state(task_type<R> task)
        : task_(task)
    {
    }

    virtual void do_work(boost::thread* thread)
    {
        if (state_ != TaskState::CANCELLED)
        {
            thread_ = thread;
            state_ = TaskState::RUNNING;
            result_ = task_();
            state_ = TaskState::COMPLETE;
            thread_ = nullptr;
            condition_.notify_all();
        }
    }

private:
    task_type<R> task_;
    R result_;
};

template <>
class state<void> : public base_state
{
    friend class future<void>;
public:
    state(task_type<void> task);

    virtual void do_work(boost::thread* thread)
    {
        if (state_ != TaskState::CANCELLED)
        {
            thread_ = thread;
            state_ = TaskState::RUNNING;
            task_();
            state_ = TaskState::COMPLETE;
            thread_ = nullptr;
            condition_.notify_all();
        }
        else
        {
            log::cerr("Cancelled job on", thread->get_id());
        }
    }

private:
    task_type<void> task_;
};

#endif // STATE_H
