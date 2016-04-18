#include "ServerTask.hpp"

namespace communication {

// static methods

boost::atomic<ServerTask*> ServerTask::head;
boost::atomic_int ServerTask::task_id_cnt(0);

int32_t ServerTask::push_front(const ServerTask &task) {

    ++task_id_cnt;
    auto p = new ServerTask(task);
    p->m_a = task.m_a;
    p->m_b = task.m_b;
    p->m_p = task.m_p;
    p->m_m = task.m_m;
    p->m_n = task.m_n;

    p->next = ServerTask::head.load();
    p->set_task_id(task_id_cnt);

    while (ServerTask::head.compare_exchange_weak(p->next, p))
    {}

    return task_id_cnt;
}

ServerTask* ServerTask::find(int32_t id) {
    auto p = ServerTask::head.load();

    while (p && p->get_task_id() != id && p->get_task_id() > 0)
        p = p->next;

    return p;
}

const std::vector<ListTasksResponse_TaskDescription> ServerTask::get_list() {
    auto p = ServerTask::head.load();
    std::vector<ListTasksResponse_TaskDescription> states;

    while (p && p->get_task_id() > 0) {
        ListTasksResponse_TaskDescription task;
        task.set_taskid(p->get_task_id());
        task.set_allocated_clientid(new std::string(p->get_client_id()));
        task.set_allocated_task(&(p->m_task));

        if (p->get_ready_param()) {
            task.set_result(p->get_result());
        }

        states.push_back(task);
        p = p->next;
    }

    return states;
}

// ServerTask implementation
ServerTask::ServerTask() {
    m_task_id = -1;
}

ServerTask::ServerTask(const ServerTask & server_task) :
                    m_ready_mutex(server_task.m_ready_mutex){
    m_task_id = server_task.get_task_id();
}

ServerTask::ServerTask(const Task & task) :
                    m_ready_mutex(new boost::mutex()) {
    m_a = task.a().value();
    m_b = task.b().value();
    m_p = task.p().value();
    m_m = task.m().value();
    m_n = task.n();
    m_task = task;
}

ServerTask::~ServerTask() {
}


void ServerTask::run() {
    check_params();

    while (m_n-- > 0)
    {
        m_b = (m_a * m_p + m_b) % m_m;
        m_a = m_b;
    }

    m_result = m_a;

    notify_all();
}

int64_t ServerTask::subscribe() {
    if (m_ready) {
        return m_result;
    }

    boost::unique_lock<boost::mutex> lock(*m_ready_mutex);
    while (!m_ready)
    {
        m_ready_cond.wait(lock);
    }

    return m_result;
}

void ServerTask::check_params() {
    if (m_task.a().has_dependenttaskid()) {
        m_a = wait_for_dependent_task(m_task.a().dependenttaskid());
    }

    if (m_task.b().has_dependenttaskid()) {
        m_b = wait_for_dependent_task(m_task.b().dependenttaskid());
    }

    if (m_task.p().has_dependenttaskid()) {
        m_p = wait_for_dependent_task(m_task.p().dependenttaskid());
    }

    if (m_task.m().has_dependenttaskid()) {
        m_m = wait_for_dependent_task(m_task.m().dependenttaskid());
    }
}

int64_t ServerTask::wait_for_dependent_task(int32_t task_id) {
    // wait for dependent task will be done
    ServerTask* task = ServerTask::find(task_id);
    boost::unique_lock<boost::mutex> lock(*(task->get_mutex()));
    if (m_ready) {
        return m_result;
    }

    while (!task->get_ready_param())
    {
        task->get_cond().wait(lock);
    }
}

void ServerTask::notify_all() {
    boost::unique_lock<boost::mutex> lock(*m_ready_mutex);
    m_ready = true;
    m_ready_cond.notify_all();
}

int64_t ServerTask::get_result() const {
    return m_result;
}

int32_t ServerTask::get_task_id() const {
    return m_task_id;
}

const std::string& ServerTask::get_client_id() const {
    return m_client_id;
}

boost::condition_variable & ServerTask::get_cond() {
    return m_ready_cond;
}

boost::shared_ptr<boost::mutex> ServerTask::get_mutex() {
    return m_ready_mutex;
}

const bool ServerTask::get_ready_param() const {
    return m_ready;
}

void ServerTask::set_params_from(const Task & task) {
    m_a = task.a().value();
    m_b = task.b().value();
    m_p = task.p().value();
    m_m = task.m().value();
    m_n = task.n();
    m_task = task;
}

void ServerTask::set_task_id(int32_t task_id) {
    m_task_id = task_id;
}

void ServerTask::set_client_id(std::string & client) {
    m_client_id = client;
}

// @end of ServerTask implementation


} // namespace communication
