#include "TaskQueue.h"
#include "AsyncService.h"
#include "../protocol/common.h"
template<class T>                                                                                               
struct ValueHolder                                                                                              
{                                                                                                               
    ValueHolder() : m_empty(true) { }                                                                           
    ValueHolder(const T & data) : m_data(data), m_empty(false) { }                                              
    T & get() const { m_empty = false; return m_data; }                                                               
    operator bool() const { return !m_empty; }                                                                  
    friend std::ostream & operator<<(std::ostream & s, const ValueHolder<T> & v) {
        if (v) {                                                                                                
            return s << v.m_data;                                                                               
        } else {                                                                                                
            return s << "empty";                                                                                
        }                                                                                                       
    }                                                                                                           
    private:                                                                                                    
        mutable T m_data;                                                                                               
        mutable bool m_empty;                                                                                           
};

TaskQueue::TaskQueue(std::condition_variable & main_threads) : main_threads_(main_threads)
{
}

size_t TaskQueue::size()
{
    return queue_.size();
}

void TaskQueue::add_message(const communication::WrapperMessage & message, const boost::shared_ptr<AsyncService> & service)
{
    if (!protocol::is_request(message)) {
        return;
    }

    if (!protocol::is_correct_server_request(message.request())) {
        return;
    }

    if (message.request().has_submit()) {
        if (!protocol::is_correct_submit_task(message.request().submit())) {
            service->send_message(protocol::SubmitTaskResponse(message.request().request_id(), 0, communication::ERROR));
            return;
        }
    } else if (message.request().has_subscribe()) {
        if (!protocol::is_correct_subscribe(message.request().subscribe())) {
            service->send_message(protocol::SubscribeResponse(message.request().request_id(), 0, communication::ERROR));
            return;
        }
    } else if (message.request().has_list()) {
        if (!protocol::is_correct_list_tasks(message.request().list())) {
            service->send_message(protocol::ListTaskResponse(message.request().request_id(), communication::ERROR));
            return;
        }
    }

    {
        std::lock_guard<boost::recursive_mutex> guard(mutex_);
        queue_.push(std::make_pair(message, service));
    }

    main_threads_.notify_one();
}

std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> TaskQueue::get_task()
{
    std::lock_guard<boost::recursive_mutex> guard(mutex_);
    if (queue_.size() > 0) {
        auto element = queue_.front(); 
        queue_.pop();
        return element;
    }

    throw std::invalid_argument("Count elements in queue incorrect");
}
