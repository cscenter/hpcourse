#ifndef __RUNNING_AND_DONE_QUEUE__
#define __RUNNING_AND_DONE_QUEUE__

#include <unordered_map>
#include <mutex>
#include <boost/shared_ptr.hpp>
#include "AsyncService.h"
#include "ValueHolder.h"
#include "../metadata/protocol.pb.h"
#include "../protocol/common.h"


class RunningAndDoneQueue
{

public:
    enum class Status { UNDEFINE, RUNNING, DONE };
    bool add_task(const std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> & task, int64_t & task_id);
    std::pair<std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>>, std::vector<int64_t>> set_done(int64_t task_id, int64_t v);
    bool get_value_or_subscribe(const std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> & filter, int64_t & value);
    communication::WrapperMessage get_list_tasks(google::protobuf::int64 request_id);
    void set_running(int64_t task_id);
    int64_t get_result(int64_t task_id);
    void get_values(int64_t task_id, int64_t & a, int64_t & b, int64_t & p, int64_t & m, int64_t & n);
    bool update(int64_t sub_task_id, int64_t task_id, int64_t result);

    struct Task
    {
        ValueHolder<int64_t> a;
        ValueHolder<int64_t> b;
        ValueHolder<int64_t> p;
        ValueHolder<int64_t> m;
        communication::Task communication_task;
    };

private:
    struct Element
    {
        google::protobuf::string client_id;
        int64_t result;
        Status status;
        Task task;
        std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>> subscribers;
        std::vector<int64_t> task_ids;
    };

private:
    std::mutex mutex_;
    std::unordered_map<int64_t, Element> tasks_;
    int64_t identifier_;
};

#endif //__RUNNING_AND_DONE_QUEUE__
