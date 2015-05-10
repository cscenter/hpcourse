#include "state.h"

void base_state::wait_for_result()
{
    boost::unique_lock<boost::mutex> guard(mutex_);
    condition_.wait(guard, [&]{return state_ == TaskState::COMPLETE || state_ == TaskState::CANCELLED;});
}

void base_state::cancel()
{
    state_ = TaskState::CANCELLED;
    if (thread_ != nullptr)
    {
        thread_.load()->interrupt();
    }
}

size_t base_state::get_id()
{
    return id_;
}

void base_state::set_exception(std::__exception_ptr::exception_ptr ptr)
{
    state_ = TaskState::COMPLETE;
    condition_.notify_all();
    exception_ = ptr;
}

std::__exception_ptr::exception_ptr base_state::exception() const
{
    return exception_;
}

TaskState base_state::status()
{
    return state_;
}

base_state::~base_state()
{
}

base_state::base_state() : state_(TaskState::IDLE), thread_(nullptr)
{
}

state<void>::state(task_type<void> task)
    : task_(task)
{
}
