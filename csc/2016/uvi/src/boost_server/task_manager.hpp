#ifndef PARALLEL_TASK_MANAGER_HPP
#define PARALLEL_TASK_MANAGER_HPP

//#include <stdint.h>
#include <mutex>
#include <vector>
#include <string>
#include <thread>
#include <condition_variable>
#include <unordered_map>
#include "protocol.pb.h"
#include "task.hpp"

struct task_manager {

public:
    task_manager();
    // noncopyable
    task_manager(task_manager const&) = delete;
    task_manager& operator=(task_manager const&) = delete;

    // возвращает идентификатор запущенной задачи и статус
    communication::SubmitTaskResponse submit_task(std::string client_id, int64_t request_id,
                                                  communication::Task args, int64_t n);
    // возвращает результат задачи task_id
    communication::SubscribeResponse subscribe(int32_t task_id);
    communication::ListTasksResponse list_tasks();



//private:
    std::mutex        task_mutex_;
    std::mutex        list_mutex_;
    std::vector<task> tasks;

    // пара: поток/идентификатор того, что поток закончил считать
    //std::unordered_map<bool, std::thread> threads;
    //std::vector<std::pair<std::thread, bool>> threads_;
    //std::queue
private:
    int64_t get_parameter(communication::Task_Param const &param, size_t cur_tasks_size);
    void wait_for_task_to_finish(int64_t task_id);
};

#endif //PARALLEL_TASK_MANAGER_HPP
