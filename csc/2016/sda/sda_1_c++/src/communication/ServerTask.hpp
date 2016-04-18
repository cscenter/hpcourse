#ifndef SERVERTASK
#define SERVERTASK

#include <boost/thread/thread.hpp>
#include <boost/atomic.hpp>

#include "protocol.pb.h"

namespace communication {

// Wrapper for original task
class ServerTask {

public:

    static boost::atomic<ServerTask*> head;
    static boost::atomic_int task_id_cnt;

    static int32_t push_front(const ServerTask &task);
    static ServerTask* find(int32_t id);
    static const std::vector<ListTasksResponse_TaskDescription> get_list();

public:
    ServerTask();
    ServerTask(const ServerTask & server_task);
    ServerTask(const Task & task);
    ~ServerTask();
    void        run();
    int64_t     subscribe();
    void        check_params();
    void        notify_all();

    int64_t     get_result() const;
    int32_t     get_task_id() const;
    const std::string&     get_client_id() const;

    void        set_params_from(const Task & task);
    void        set_task_id(int32_t task_id);
    void        set_client_id(std::string & client);

    int64_t     wait_for_dependent_task(int32_t task_id);

    //cond
    boost::condition_variable &     get_cond();
    boost::shared_ptr<boost::mutex> get_mutex();
    const bool                      get_ready_param() const;

public:
    ServerTask* next;

private:
    Task        m_task;
    int64_t     m_result;
    std::string     m_client_id;
    int32_t     m_task_id;
    int64_t     m_a, m_b, m_p, m_m, m_n;

    // cond
    boost::condition_variable       m_ready_cond;
    boost::shared_ptr<boost::mutex> m_ready_mutex;
    bool                            m_ready = false;

}; // ServerTask


} // namespace communication


#endif /* end of include guard: ServerTask */
