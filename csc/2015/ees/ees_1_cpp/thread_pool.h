#ifndef THREAD_POOL_H
#define THREAD_POOL_H

#include "task_queue.h"
#include "log.h"
#include "state.h"

#include <iostream>
#include <functional>
#include <memory>
#include <queue>
#include <thread>
#include <atomic>

#include <boost/thread.hpp>
#include <boost/chrono.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/condition_variable.hpp>
#include <boost/thread/lock_types.hpp>

class thread_pool
{
    template <typename> friend class future;
    template <typename> friend class future_base;
public:
    thread_pool(uint thread_num);
    ~thread_pool();

    template <class Fn, class... Args>
    future<typename std::result_of<Fn(Args...)>::type>
    submit(Fn func, Args... args);

private:
    uint thread_count_;
    boost::thread_group threads_;
    boost::barrier barrier_;

    task_queue<std::shared_ptr<base_state>> task_queue_;
    std::atomic_bool shutdown_;
    std::map<boost::thread::id, boost::thread*> thread_ids_;

    void thread_routine();
};

template <class R>
class future_base
{
public:
    future_base(thread_pool* pool, std::shared_ptr<state<R>> future_state)
        : state_(future_state), pool_(pool)
    {
    }

    future_base(future_base&& other) = default;

    void cancel()
    {
        state_->cancel();
    }

    void wait()
    {
        do_work_if_needed();
        state_->wait_for_result();
    }

    void get_id()
    {
        return state_->get_id();
    }

    TaskState status()
    {
        return state_->status();
    }

protected:
    std::shared_ptr<state<R>> state_;
    thread_pool* pool_;
    size_t id_;

    void do_work_if_needed()
    {
        if (pool_->threads_.is_this_thread_in())
        {
            state_->do_work(pool_->thread_ids_[boost::this_thread::get_id()]);
        }
    }
};

template <class R>
class future : public future_base<R>
{
    typedef future_base<R> super;
public:
    future(thread_pool* pool, std::shared_ptr<state<R>> future_state)
      : super(pool, future_state)
    {
    }

    future(future&& other)
        : super(std::forward<future_base<R>>(other))
    {
    }

    R get()
    {
        super::wait();

        if (std::exception_ptr ptr = super::state_->exception())
            std::rethrow_exception(ptr);

        return super::state_->result_;
    }
};

template <>
class future<void> : public future_base<void>
{
    typedef future_base<void> super;
public:
    future(thread_pool* pool, std::shared_ptr<state<void>> future_state)
      : super(pool, future_state)
    {
    }

    future(future&& other)
        : super(std::forward<super>(other))
    {
    }

    void get()
    {
        super::wait();

        if (std::exception_ptr ptr = state_->exception())
            std::rethrow_exception(ptr);
    }
};

template <class Fn, class... Args>
future<typename std::result_of<Fn(Args...)>::type>
thread_pool::submit(Fn func, Args... args)
{
    typedef typename std::result_of<Fn(Args...)>::type result_type;
    task_type<result_type> task = std::bind(func, args...);

    auto shared_state = std::make_shared<state<result_type>>(task);
    task_queue_.push(shared_state);

    return future<result_type>(this, shared_state);
}


#endif // THREAD_POOL_H
