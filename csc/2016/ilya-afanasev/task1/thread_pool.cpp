#include <thread>
#include "thread_pool.h"

thread_pool::thread_pool()
{
  unsigned int threads_num = boost::thread::hardware_concurrency();
  _threads.resize(threads_num);

  for (int i = 0; i < threads_num ; ++i)
  {
    _threads[i] = boost::thread(run);
  }
}

bool thread_pool::check_request(const communication::ServerRequest& request)
{
  boost::mutex::scoped_lock solved_tasks_lock(_tasks_sync);

  if (request.has_submit())
  {
    if (request.submit().task().a().has_dependenttaskid()
        && _tasks.find(request.submit().task().a().dependenttaskid()) != _tasks.end())
    {
      return false;
    }

    if (request.submit().task().a().has_dependenttaskid()
        && _tasks.find(request.submit().task().b().dependenttaskid()) != _tasks.end())
    {
      return false;
    }

    if (request.submit().task().a().has_dependenttaskid()
        && _tasks.find(request.submit().task().p().dependenttaskid()) != _tasks.end())
    {
      return false;
    }

    if (request.submit().task().a().has_dependenttaskid()
        && _tasks.find(request.submit().task().m().dependenttaskid()) != _tasks.end())
    {
      return false;
    }
  }
  else if (request.has_subscribe())
  {
    if (_tasks.find(request.subscribe().taskid()) != _tasks.end())
    {
      return false;
    }
  }
  return true;
}

void thread_pool::put_command(const communication::ServerRequest& command
                              , std::function<void(const communication::ServerResponse&)> callback)
{
  boost::unique_lock new_tasks_lock(_new_tasks_sync);
  _new_tasks.emplace(std::make_pair(command, callback));
  _new_tasks_cv.notify_all();
}

void thread_pool::run()
{
  while (true)
  {
    boost::this_thread::interruption_point();

    std::function<void(const communication::ServerResponse&)> callback;
    communication::ServerRequest task;

    {
      boost::unique_lock lock(_new_tasks_sync);

      while (_new_tasks.empty())
      {
        _new_tasks_cv.wait(lock);
      }
      task = _new_tasks.front().first;
      callback = _new_tasks.front().second;
      _new_tasks.pop();
    }

    if (task.has_submit())
    {
      if (check_request(task))
      {

      }
    }
    else if (task.has_subscribe())
    {

    }
    else if (task.has_list())
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

