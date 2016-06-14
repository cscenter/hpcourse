#include "CommonThreadPool.h"
#include "AsyncService.h"

CommonThreadPool::CommonThreadPool(size_t size, TaskQueue & task_queue, RunningAndDoneQueue & results, std::condition_variable & threads_notify_cond_var) : calculation_function_(nullptr), size_(size), task_queue_(task_queue), results_(results), threads_notify_cond_var_(threads_notify_cond_var)
{
    run();
}

void CommonThreadPool::run()
{
    pool_threads_.resize(size_);
    for (int i = 0; i < size_; i++) {
        pool_threads_[i] = std::thread(&CommonThreadPool::task, this);
    }
}

void CommonThreadPool::task()
{
    std::mutex mutex;
    std::unique_lock<std::mutex> lock(mutex);
    while (true) {
        threads_notify_cond_var_.wait(lock);
        if (calculation_function_ == nullptr) {
            continue;
        }
        while (true) {
            try {
                auto element = task_queue_.get_task();
                int64_t request_id = element.first.request().request_id();
                if (element.first.request().has_list()) {
                    element.second->send_message(results_.get_list_tasks(request_id));
                } else if (element.first.request().has_subscribe()) {
                    int64_t value;
                    try {
                        if (results_.get_value_or_subscribe(element, value)) {
                            element.second->send_message(protocol::SubscribeResponse(request_id, value, communication::OK));
                        }
                    } catch (std::exception & ex) {
                        element.second->send_message(protocol::SubscribeResponse(request_id, 0, communication::ERROR));
                    }
                } else if (element.first.request().has_submit()) {
                    int64_t task_id;
                    try {
                        if (results_.add_task(element, task_id)) {
                            int64_t a, b, p, m, n;
                            results_.get_values(task_id, a, b, p, m, n);
                            if (m == 0 || n > 1e9) {
                                element.second->send_message(protocol::SubmitTaskResponse(request_id, 0, communication::ERROR));
                            } else {
                                element.second->send_message(protocol::SubmitTaskResponse(request_id, task_id, communication::OK));
                                int64_t res = calculation_function_(a, b, p, m, n);
                                auto subscribers = results_.set_done(task_id, res);
                                process_subscriber(task_id, res, subscribers);
                            }
                        }
                    } 
                    catch (std::exception & ex) {
                        element.second->send_message(protocol::SubmitTaskResponse(request_id, 0, communication::ERROR));
                    }
                }
            } catch (std::exception & ex) {
                break;
            }
        }
    }
}

void CommonThreadPool::process_subscriber(int64_t task_id, int64_t result,
                                          const std::pair<std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>>, std::vector<int64_t>>  & subscribers)
{
    for (int i = 0; i < subscribers.first.size(); i++) {
        auto & sub = subscribers.first[i];
        auto & sub_task_id = subscribers.second[i];
        if (sub.first.request().has_subscribe()) {
            sub.second->send_message(protocol::SubscribeResponse(sub.first.request().request_id(),
                                         results_.get_result(sub.first.request().subscribe().taskid()),
                                         communication::OK));
        } else {
            if (results_.update(sub_task_id, task_id, result)) {
                int64_t a, b, p, m, n;
                results_.get_values(sub_task_id, a, b, p, m, n); 
                results_.set_running(sub_task_id);
                sub.second->send_message(protocol::SubmitTaskResponse(sub.first.request().request_id(), task_id, communication::OK));
                int64_t res = calculation_function_(a, b, p, m, n);
                auto subscribers = results_.set_done(task_id, res);
                process_subscriber(task_id, res, subscribers);
            }
        }
    }
}
                                          

void CommonThreadPool::set_calculation_function(int64_t (*task)(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n))
{
    calculation_function_ = task;
}
