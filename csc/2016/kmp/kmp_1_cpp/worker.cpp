#include <thread>
#include <iostream>

#include "worker.h"

unsigned int Worker::handle_submit_task(communication::SubmitTask const & submitTask, std::string const & client_id, int64_t request_id)
{
  std::unique_lock<std::mutex> lck(m_mut);
  
  unsigned int task_id = add_task(submitTask, client_id, request_id);
  std::thread(&Worker::start, this, task_id, m_tasks[task_id].args).detach();
  
  return task_id;
}

void Worker::get_task_list(std::vector<Task> & out)
{
  std::unique_lock<std::mutex> lck(m_mut);
  out = m_tasks;
}

bool Worker::subscribe(unsigned int id, bool & result_set, int64_t & result
, bool & success)
{
  if (id < m_id)
  {
    std::unique_lock<std::mutex> lck(m_mut);

    if (m_tasks[id].finished)
    {
      result_set = true;
      result = m_tasks[id].result;
      success = m_tasks[id].success;
    }
    else
    {
      result_set = false;
    }
    
    return true;
  }
  else
    return false;
}

unsigned int Worker::add_task(communication::SubmitTask const & submitTask, std::string const & client_id, int64_t request_id)
{
  m_tasks.emplace_back(m_id, submitTask.task(), client_id, request_id);
  ++m_id;

  return m_id - 1;
}

void Worker::start(unsigned int self_id, communication::Task args)
{
  int64_t a_value = try_get_param(args.a());
  int64_t b_value = try_get_param(args.b());
  int64_t p_value = try_get_param(args.p());
  int64_t m_value = try_get_param(args.m());

  work(self_id, a_value, b_value, p_value, m_value, args.n());
}

void Worker::work(int self_id, int64_t a, int64_t b, int64_t p, int64_t m, int64_t n)
{
  int64_t res = 0;
  bool success = false;

  if (m != 0)
  {
    while (n-- > 0)
    {
      b = (a * p + b) % m;
      a = b;
    }

    res = a;
    success = true;
  }

  std::unique_lock<std::mutex> lck(m_mut);

  Task & self_task = m_tasks[self_id];
  self_task.result = res;
  self_task.finished = true;
  self_task.success = success;

  self_task.cv->notify_all();
  self_task.cv.reset();

  if (m_consumer_func)
  {
    m_consumer_func(self_id, self_task.request_id, res, success);
  }
}

int64_t Worker::try_get_param(communication::Task_Param const & param)
{
  int64_t res = 0;
  
  if (param.has_dependenttaskid())
  {
    int param_id = param.dependenttaskid();
    
    std::unique_lock<std::mutex> lck(m_mut);
    
    while (!m_tasks[param_id].finished)
    {
      m_tasks[param_id].cv->wait(lck);
    }
    
    res = m_tasks[param.dependenttaskid()].result;
    
    lck.unlock();
  }
  else
  {
    res = param.value();
  }
  
  return res;
}
