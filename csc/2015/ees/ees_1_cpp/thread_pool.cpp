#include "thread_pool.h"

thread_pool::thread_pool(uint thread_num)
    : thread_count_(thread_num),
      barrier_(thread_num + 1),
      shutdown_(false)
{
    for (uint i = 0; i < thread_num; ++i)
    {
        auto thread = threads_.create_thread(std::bind(&thread_pool::thread_routine, this));
        thread_ids_[thread->get_id()] = thread;
    }

    barrier_.wait();
}

thread_pool::~thread_pool()
{
    shutdown_ = true;
    threads_.interrupt_all();
    threads_.join_all();
}

void thread_pool::thread_routine()
{
    barrier_.wait();

    assert(thread_ids_.find(boost::this_thread::get_id()) != thread_ids_.end());
    while (!shutdown_)
    {
        std::shared_ptr<base_state> task_state = task_queue_.pop();
        try
        {
            task_state->do_work(thread_ids_[boost::this_thread::get_id()]);
        }
        catch(const boost::thread_interrupted& e)
        {
            log::cout("Canceled job on thread: ", boost::this_thread::get_id());
        }
        catch (...)
        {
            std::exception_ptr exception = std::current_exception();
            task_state->set_exception(exception);
        }
    }
}
