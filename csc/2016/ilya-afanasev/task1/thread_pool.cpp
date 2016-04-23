#include <thread>
#include "thread_pool.h"

thread_pool::thread_pool()
{
  unsigned int threads_num = boost::thread::hardware_concurrency();
  _threads.resize(threads_num);

  for (int i = 0; i < threads_num ; ++i)
  {
    _threads[i] = boost::thread(&thread_pool::run, this);
  }
}

void thread_pool::update_task_param(communication::Task_Param& param)
{
  if (param.has_dependenttaskid())
  {
    if (_tasks[param.dependenttaskid()].has_result())
    {
      uint64_t result = _tasks[param.dependenttaskid()].result();
      param.set_value(result);
    }
  }
}

void thread_pool::update_dependencies(communication::SubmitTask& task)
{
  update_task_param(*task.mutable_task()->mutable_a());
  update_task_param(*task.mutable_task()->mutable_b());
  update_task_param(*task.mutable_task()->mutable_p());
  update_task_param(*task.mutable_task()->mutable_m());
}

bool thread_pool::has_dependencies(const communication::SubmitTask& task)
{
  return task.task().a().has_dependenttaskid()
         || task.task().b().has_dependenttaskid()
         || task.task().p().has_dependenttaskid()
         || task.task().m().has_dependenttaskid();
}

bool thread_pool::check_subscribe_task(const communication::Subscribe& task)
{
  return _tasks.find(task.taskid()) != _tasks.end();
}

bool thread_pool::check_submit_task(const communication::SubmitTask& task)
{
  boost::mutex::scoped_lock solved_tasks_lock(_tasks_sync);

  if (task.task().a().has_dependenttaskid()
      && _tasks.find(task.task().a().dependenttaskid()) == _tasks.end())
  {
    return false;
  }

  if (task.task().b().has_dependenttaskid()
      && _tasks.find(task.task().b().dependenttaskid()) == _tasks.end())
  {
    return false;
  }

  if (task.task().p().has_dependenttaskid()
      && _tasks.find(task.task().p().dependenttaskid()) == _tasks.end())
  {
    return false;
  }

  if (task.task().m().has_dependenttaskid()
      && _tasks.find(task.task().m().dependenttaskid()) == _tasks.end())
  {
    return false;
  }
  return true;
}


void thread_pool::put_command(const communication::ServerRequest& command
                              , std::function<void(const communication::ServerResponse&)> callback)
{
  boost::unique_lock<boost::mutex> new_tasks_lock(_new_tasks_sync);
  _new_tasks.emplace(std::make_pair(command, callback));
  _new_tasks_cv.notify_all();
}

void thread_pool::run()
{
  while (true)
  {
    boost::this_thread::interruption_point();

    std::function<void(const communication::ServerResponse&)> callback;
    communication::ServerRequest request;

    {
      boost::unique_lock<boost::mutex> lock(_new_tasks_sync);

      while (_new_tasks.empty())
      {
        _new_tasks_cv.wait(lock);
      }
      request = _new_tasks.front().first;
      callback = _new_tasks.front().second;
      _new_tasks.pop();
    }

    if (request.has_submit())
    {
      if (check_submit_task(request.submit()))
      {
        {
          boost::unique_lock<boost::mutex> lock(_tasks_sync);

          while (has_dependencies(request.submit()))
          {
            _tasks_cv.wait(lock);
            update_dependencies(*request.mutable_submit());
          }
        }

        int id = _uniq_id++;

        communication::ListTasksResponse_TaskDescription description;
        description.set_taskid(id);

        description.set_allocated_clientid(new std::string(request.client_id()));
        description.set_allocated_task(new communication::Task(request.submit().task()));

        {
          boost::unique_lock<boost::mutex> lock(_tasks_sync);
          _tasks[id] = description;
        }

        communication::Task& task = *request.mutable_submit()->mutable_task();
        int64_t result = run_task(task.a().value(), task.b().value(), task.p().value(), task.m().value(), task.n());

        boost::unique_lock<boost::mutex> lock(_tasks_sync);
        _tasks[id].set_result(result);
      }
    }
    else if (request.has_subscribe())
    {
      if (check_subscribe_task(request.subscribe()))
      {

      }
      else
      {

      }
    }
    else if (request.has_list())
    {

    }
  }
}

int64_t thread_pool::run_task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n)
{
  while (n-- > 0)
  {
    b = (a * p + b) % m;
    a = b;
  }
  return a;
}