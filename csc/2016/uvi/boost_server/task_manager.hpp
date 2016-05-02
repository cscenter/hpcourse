#ifndef PARALLEL_TASK_MANAGER_HPP
#define PARALLEL_TASK_MANAGER_HPP

#include <mutex>
#include <vector>
#include <list>
#include <string>
#include <thread>
#include <condition_variable>
#include <unordered_map>
#include "protocol.pb.h"
#include "task.hpp"

struct task_manager {

public:
    task_manager();
    task_manager(task_manager const&) = delete;
    task_manager& operator=(task_manager const&) = delete;

    communication::SubmitTaskResponse submit_task(std::string client_id, int64_t request_id,
                                                  communication::Task args, int64_t n);
    communication::SubscribeResponse subscribe(int32_t task_id);
    communication::ListTasksResponse list_tasks();

private:
    std::mutex        task_mutex_;
    std::mutex        list_mutex_;
    std::vector<task> tasks;

private:
    int64_t get_parameter(communication::Task_Param const &param, size_t cur_tasks_size);
    void wait_task(int64_t task_id);
};

#endif //PARALLEL_TASK_MANAGER_HPP
