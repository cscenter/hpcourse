#include <sstream>
#include <string>
#include <map>
#include <iostream>
#include "task_manager.hpp"

task_manager::task_manager()
{
    const int32_t res_size = 1e6;
    tasks.reserve(res_size);
}

communication::SubmitTaskResponse
task_manager::submit_task(std::string client_id, int64_t request_id, communication::Task args, int64_t n)
{
    // тут вроде хватать мьютекс не надо
    size_t cur_tasks_size = tasks.size();

    int64_t a = get_parameter(args.a(), cur_tasks_size);
    int64_t b = get_parameter(args.b(), cur_tasks_size);
    int64_t p = get_parameter(args.p(), cur_tasks_size);
    int64_t m = get_parameter(args.m(), cur_tasks_size);

    // если параметр - ошибочный
    communication::SubmitTaskResponse subm_resp;
    if (a == -1 || b == -1 || p == -1 || m == -1)
    {
        subm_resp.set_status(communication::Status::ERROR);
        return subm_resp;
    }

    int64_t task_id = -1;
    {
        std::unique_lock<std::mutex> lock_(task_mutex_);

        tasks.push_back(task(a, b, p, m, n, request_id, client_id));

        task_id = tasks.size() - 1;
    }

//
//     потоки - через  unord map, пока не работает
//    auto it_thr = threads.find(false);
//    if (it_thr != threads.end()) {
//
//        it_thr->second(&task::calculate, &tasks[task_id],
//                       a, b, p, m, n, std::ref(task_mutex_));
//    }
//
//
//     по идее, поток отсоединили, он полетел что-то считать,
//     а мы, не дожидаясь его, отправляем ответ - статус и id.
    std::thread(&task::calculate, &tasks[task_id], std::ref(task_mutex_)).detach();

    // возвращаемый статус и идентификатор - номер позиции в векторе задач
    subm_resp.set_status(communication::Status::OK);
    subm_resp.set_submittedtaskid(task_id);
    return subm_resp;
}

communication::SubscribeResponse task_manager::subscribe(int32_t task_id)
{
    communication::SubscribeResponse subs_resp;

    if (task_id < 0 || task_id >= tasks.size())
    {
        subs_resp.set_status(communication::Status::ERROR);
        return subs_resp;
    }
    
    wait_task(task_id);

    // возвращаем результат, когда задача уже выполнилась
    subs_resp.set_status(communication::Status::OK);
    subs_resp.set_value(tasks[task_id].get_result());

    return subs_resp;
}


communication::ListTasksResponse task_manager::list_tasks()
{
    communication::ListTasksResponse list_resp;

    list_resp.set_status(communication::Status::OK);

    std::unique_lock<std::mutex> list_lock(task_mutex_);
    for (int i = 0; i < tasks.size(); ++i)
    {
        list_resp.mutable_tasks()->Add();

        int64_t task_id = i;

        auto tmp_i = list_resp.mutable_tasks(task_id);

        tmp_i->set_taskid(task_id);
        tmp_i->set_clientid(tasks[task_id].get_client_id());

        tmp_i->mutable_task()->mutable_a()->set_value(tasks[task_id].get_a());
        tmp_i->mutable_task()->mutable_b()->set_value(tasks[task_id].get_b());
        tmp_i->mutable_task()->mutable_p()->set_value(tasks[task_id].get_p());
        tmp_i->mutable_task()->mutable_m()->set_value(tasks[task_id].get_m());
        tmp_i->mutable_task()->set_n(tasks[task_id].get_n());

        auto res = tasks[task_id].get_result();
        if (res >= 0)
        {
            tmp_i->set_result(res);
        }
    }
    return list_resp;
}


int64_t task_manager::get_parameter(communication::Task_Param const &parameter, size_t cur_tasks_size)
{
    if (!parameter.has_dependenttaskid())
    {
        return parameter.value();
    }

    // если параметр - зависимая переменная
    // если параметр - неправильный id какой-то задачи
    if (parameter.dependenttaskid() < 0 || parameter.dependenttaskid() > cur_tasks_size - 1)
    {
        return -1;
    }

    wait_task(parameter.dependenttaskid());
    return tasks[parameter.dependenttaskid()].get_result();
}

void task_manager::wait_task(int64_t task_id)
{
    std::unique_lock<std::mutex> wait_lock(task_mutex_);

    while (tasks[task_id].get_result() < 0)
    {
        tasks[task_id].get_cond_var_ptr()->wait(wait_lock);
    }
}


