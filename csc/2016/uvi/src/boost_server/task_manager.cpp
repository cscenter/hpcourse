#include <sstream>
#include <string>
#include <map>
#include <iostream>
#include "task_manager.hpp"

task_manager::task_manager() {}

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
        //subm_resp.set_submittedtaskid(-1);
        return subm_resp;
    }

    int32_t task_id = -1;
    {
//      std::cout << "Submitting response. Params in scope: " << a << ' ' << b << ' ' << p << ' ' << m << ' ' << n << ' ' << std::endl;

        std::unique_lock<std::mutex> lock_(task_mutex_);




        // почему в векторе в результате параметры нулевые?????????????????????????????????????????????????????????????????????????????????????????
        // иначе приходится ниже писать set_a... и тд
        // а что если делать в вектор мув? Правда тут временное значение
        // и что если вообще запретить копирование task




        tasks.push_back(task(a, b, p, m, n, request_id, client_id));

        std::cout << std::endl << "TASKS CLIENT_ID: " << tasks[tasks.size() - 1].get_client_id() << std::endl;
        std::cout << "TASKS REQUEST_ID: " << tasks[tasks.size() - 1].get_request_id() << std::endl;
        std::cout << "TASKS A: " << tasks[tasks.size() - 1].get_a() << std::endl;
        std::cout << "TASKS B: " << tasks[tasks.size() - 1].get_b() << std::endl;
        std::cout << "TASKS P: " << tasks[tasks.size() - 1].get_p() << std::endl;
        std::cout << "TASKS M: " << tasks[tasks.size() - 1].get_m() << std::endl;
        std::cout << "TASKS N: " << tasks[tasks.size() - 1].get_n() << std::endl;
        std::cout << "TASKS RESULT: " << tasks[tasks.size() - 1].get_result() << std::endl << std::endl;

        task_id = tasks.size() - 1;

//        tasks[task_id].set_client_id(client_id);
//        tasks[task_id].set_request_id(request_id);
//        tasks[task_id].set_a(a);
//        tasks[task_id].set_b(b);
//        tasks[task_id].set_p(p);
//        tasks[task_id].set_m(m);
//        tasks[task_id].set_n(n);

//        std::cout << "Submitting response. Params in task vector: " << tasks[task_id].get_client_id() << ' ' << tasks[task_id].get_request_id() << ' ' << tasks[task_id].get_a() << ' ' << tasks[task_id].get_b() << ' ' << tasks[task_id].get_p() << ' ' << tasks[task_id].get_m() << ' ' << tasks[task_id].get_n() << ' ' << std::endl;

    }


//     потоки - через  unord map, пока не работает
//    auto it_thr = threads.find(false);
//    if (it_thr != threads.end()) {
//
//        it_thr->second(&task::calculate, &tasks[task_id],
//                       a, b, p, m, n, std::ref(task_mutex_));
//    }

    // по идее, поток отсоединили, он полетел что-то считать,
    // а мы, не дожидаясь его, отправляем ответ - статус и id.
    std::thread(&task::calculate, &tasks[task_id], std::ref(task_mutex_)).detach();


//    std::cout << "Params in task vector after detaching: " << tasks[task_id].get_client_id() << ' ' << tasks[task_id].get_request_id() << ' ' << tasks[task_id].get_a() << ' ' << tasks[task_id].get_b() << ' ' << tasks[task_id].get_p() << ' ' << tasks[task_id].get_m() << ' ' << tasks[task_id].get_n() << ' ' << std::endl;



    // возвращаемый статус и идентификатор - номер позиции в векторе задач
    subm_resp.set_status(communication::Status::OK);
    subm_resp.set_submittedtaskid(task_id);
    return subm_resp;
}

communication::SubscribeResponse task_manager::subscribe(int32_t task_id)
{
//    std::cout << "params in task to subscribe: " << tasks[task_id].get_a() << ' ' << tasks[task_id].get_b() << ' ' << tasks[task_id].get_p() << ' ' << tasks[task_id].get_m() << ' ' << tasks[task_id].get_n() << std::endl;

    wait_for_task_to_finish(task_id);

    // возвращаем результат, когда задача уже выполнилась
    communication::SubscribeResponse subs_resp;
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
        auto task_desc_ptr = list_resp.mutable_tasks()->Add();

        task_desc_ptr->mutable_task()->mutable_a()->set_value(tasks[i].get_a());
        task_desc_ptr->mutable_task()->mutable_b()->set_value(tasks[i].get_b());
        task_desc_ptr->mutable_task()->mutable_p()->set_value(tasks[i].get_p());
        task_desc_ptr->mutable_task()->mutable_m()->set_value(tasks[i].get_m());
        task_desc_ptr->mutable_task()->set_n(tasks[i].get_n());

        if (task_desc_ptr->has_result())
        {
            task_desc_ptr->set_result(tasks[i].get_result());
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

    wait_for_task_to_finish(parameter.dependenttaskid());
    return tasks[parameter.dependenttaskid()].get_result();
}

void task_manager::wait_for_task_to_finish(int64_t task_id)
{
    std::unique_lock<std::mutex> wait_lock(task_mutex_);


    std::cout << "wainting function" << std::endl;
    std::cout << "current result of task: " << tasks[task_id].get_result() << std::endl;

    while (tasks[task_id].get_result() < 0)
    {
        std::cout << "\t\t\t\twainting..." << std::endl;
        tasks[task_id].get_cond_var_ptr()->wait(wait_lock);
    }
}


