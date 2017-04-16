#include "RunningAndDoneQueue.h"

bool RunningAndDoneQueue::add_task(const std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> & task, int64_t & task_id)
{
    if (task.first.request().submit().task().a().has_value()
        && task.first.request().submit().task().b().has_value()
        && task.first.request().submit().task().p().has_value()
        && task.first.request().submit().task().m().has_value()
        ) {
        Element element;
        element.client_id = task.first.request().client_id();
        element.status = Status::RUNNING;
        element.task.communication_task = task.first.request().submit().task();
        {
            std::lock_guard<std::mutex> guard(mutex_);
            task_id = identifier_++; 
            tasks_.insert(std::make_pair(task_id, element));
        }
        return true;
    } else {
        std::lock_guard<std::mutex> guard(mutex_);
        Element element;
        task_id = identifier_++; 
        element.client_id = task.first.request().client_id();
        element.task.communication_task = task.first.request().submit().task();
        element.status = Status::RUNNING;

        if (task.first.request().submit().task().a().has_dependenttaskid()) {
           int64_t task_id = task.first.request().submit().task().a().dependenttaskid();
           auto itr = tasks_.find(task_id);
           if (itr != tasks_.end()) {
               if (itr->second.status == Status::DONE) {
                   element.task.a.get() = itr->second.result;
               } else {
                   element.status = Status::UNDEFINE;
                   itr->second.subscribers.push_back(task);
                   itr->second.task_ids.push_back(task_id);
               }
           } else {
                throw std::invalid_argument("Incorrect task");
           }
        }

        if (task.first.request().submit().task().b().has_dependenttaskid()) {
           int64_t task_id = task.first.request().submit().task().b().dependenttaskid();
           auto itr = tasks_.find(task_id);
           if (itr != tasks_.end()) {
               if (itr->second.status == Status::DONE) {
                   element.task.b.get() = itr->second.result;
               } else {
                   element.status = Status::UNDEFINE;
                   itr->second.subscribers.push_back(task);
                   itr->second.task_ids.push_back(task_id);
               }
           } else {
                throw std::invalid_argument("Incorrect task");
           }
        }

        if (task.first.request().submit().task().p().has_dependenttaskid()) {
           int64_t task_id = task.first.request().submit().task().p().dependenttaskid();
           auto itr = tasks_.find(task_id);
           if (itr != tasks_.end()) {
               if (itr->second.status == Status::DONE) {
                   element.task.p.get() = itr->second.result;
               } else {
                   element.status = Status::UNDEFINE;
                   itr->second.subscribers.push_back(task);
                   itr->second.task_ids.push_back(task_id);
               }
           } else {
                throw std::invalid_argument("Incorrect task");
           }
        }

        if (task.first.request().submit().task().m().has_dependenttaskid()) {
           int64_t task_id = task.first.request().submit().task().m().dependenttaskid();
           auto itr = tasks_.find(task_id);
           if (itr != tasks_.end()) {
               if (itr->second.status == Status::DONE) {
                   element.task.m.get() = itr->second.result;
               } else {
                   element.status = Status::UNDEFINE;
                   itr->second.subscribers.push_back(task);
                   itr->second.task_ids.push_back(task_id);
               }
           } else {
                throw std::invalid_argument("Incorrect task");
           }
        }

        if (element.status == Status::UNDEFINE) {
            tasks_.insert(std::make_pair(task_id, element));
            return false;
        } else {
            tasks_.insert(std::make_pair(task_id, element));
            return true;
        }

    }
    throw std::invalid_argument("Incorrect task");
}

std::pair<std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>>, std::vector<int64_t>> RunningAndDoneQueue::set_done(int64_t task_id, int64_t v)
{
    std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>> subscribers;
    std::vector<int64_t> task_ids;
    {
        std::lock_guard<std::mutex> guard(mutex_);
        auto itr = tasks_.find(task_id);
        if (itr != tasks_.end()) {
            itr->second.result = v;
            itr->second.status = Status::DONE;
            subscribers = std::move(itr->second.subscribers);
            task_ids = std::move(itr->second.task_ids);
        }
    }
    return std::make_pair(subscribers, task_ids);
}

void RunningAndDoneQueue::set_running(int64_t task_id)
{
    std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>> subscribers;
    {
        std::lock_guard<std::mutex> guard(mutex_);
        auto itr = tasks_.find(task_id);
        if (itr != tasks_.end()) {
            itr->second.status = Status::RUNNING;
            subscribers = std::move(itr->second.subscribers);
        }
    }
}

int64_t RunningAndDoneQueue::get_result(int64_t task_id)
{
    std::lock_guard<std::mutex> guard(mutex_);
    auto itr = tasks_.find(task_id);
    if (itr != tasks_.end()) {
        return itr->second.result;
    }
    throw std::invalid_argument("Incorrect task_id");
}

bool RunningAndDoneQueue::get_value_or_subscribe(const std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> & filter, int64_t & value)
{
    int64_t task_id = filter.first.request().subscribe().taskid();
    std::lock_guard<std::mutex> guard(mutex_);
    auto itr = tasks_.find(task_id);
    if (itr != tasks_.end()) {
        if (itr->second.status == Status::DONE) {
            value = itr->second.result;
            return true;
        } else if (itr->second.status == Status::RUNNING) {
            itr->second.subscribers.push_back(filter);
            itr->second.task_ids.push_back(task_id);
            return false;
        }
    }
    throw std::invalid_argument("Incorrect task_id");
}

void RunningAndDoneQueue::get_values(int64_t task_id, int64_t & a, int64_t & b, int64_t & p, int64_t & m, int64_t & n)
{
    std::lock_guard<std::mutex> guard(mutex_);
    auto itr = tasks_.find(task_id);
    if (itr != tasks_.end()) {
        if (itr->second.task.communication_task.a().has_dependenttaskid()) {
            a = itr->second.task.a.get();
        } else {
            a = itr->second.task.communication_task.a().value();
        }

        if (itr->second.task.communication_task.b().has_dependenttaskid()) {
            b = itr->second.task.b.get();
        } else {
            b = itr->second.task.communication_task.b().value();
        }

        if (itr->second.task.communication_task.p().has_dependenttaskid()) {
            p = itr->second.task.p.get();
        } else {
            p = itr->second.task.communication_task.p().value();
        }

        if (itr->second.task.communication_task.m().has_dependenttaskid()) {
            m = itr->second.task.m.get();
        } else {
            m = itr->second.task.communication_task.m().value();
        }

        n = itr->second.task.communication_task.n();
        return;
    }

    throw std::invalid_argument("Incorrect sub_task_id");
}

bool RunningAndDoneQueue::update(int64_t sub_task_id, int64_t task_id, int64_t result)
{
    std::lock_guard<std::mutex> guard(mutex_);
    bool all = true;
    auto itr = tasks_.find(sub_task_id);
    if (itr != tasks_.end()) {
        if (itr->second.task.communication_task.a().has_dependenttaskid()) {
            if (task_id == itr->second.task.communication_task.a().dependenttaskid()) {
                itr->second.task.a.get() = result;
            }
            if (!itr->second.task.a) {
                all = false;
            }
        }
        if (itr->second.task.communication_task.b().has_dependenttaskid()) {
            if (task_id == itr->second.task.communication_task.b().dependenttaskid()) {
                itr->second.task.b.get() = result;
            }
            if (!itr->second.task.b) {
                all = false;
            }
        }
        if (itr->second.task.communication_task.p().has_dependenttaskid()) {
            if (task_id == itr->second.task.communication_task.p().dependenttaskid()) {
                itr->second.task.p.get() = result;
            }
            if (!itr->second.task.p) {
                all = false;
            }
        }
        if (itr->second.task.communication_task.m().has_dependenttaskid()) {
            if (task_id == itr->second.task.communication_task.m().dependenttaskid()) {
                itr->second.task.m.get() = result;
            }
            if (!itr->second.task.m) {
                all = false;
            }
        }
        return all;
    }

    throw std::invalid_argument("Incorrect sub_task_id");
}

communication::WrapperMessage RunningAndDoneQueue::get_list_tasks(google::protobuf::int64 request_id)
{
    std::vector<communication::ListTasksResponse_TaskDescription> tasks;
    {
        std::lock_guard<std::mutex> guard(mutex_);
        for (const auto & element : tasks_) {
            if (element.second.status != Status::UNDEFINE) {
                tasks.push_back(element.second.status == Status::DONE ? 
                                    protocol::TaskDescription(element.first, element.second.client_id, element.second.task.communication_task) 
                                    : protocol::TaskDescription(element.first, element.second.client_id, element.second.task.communication_task, element.second.result));
            }
        }
    }
    return protocol::ListTaskResponse(request_id, communication::OK, tasks);
}
