#include <thread>
#include <iostream>

#include "worker.h"

unsigned int Worker::handle_submit_task(communication::SubmitTask const & submitTask, std::string const & client_id, int64_t request_id)
{
  m_mut.lock();
  
  unsigned int task_id = add_task(submitTask, client_id, request_id);
  std::thread(&Worker::start, this, task_id, m_tasks[task_id].args).detach();
  
  m_mut.unlock();
  
  return task_id;
}

void Worker::get_task_list(std::vector<Task> & out)
{
  m_mut.lock();
  out = m_tasks;
  m_mut.unlock();
}

bool Worker::subscribe(unsigned int id, bool & result_set, int64_t & result)
{
  if (id < m_id)
  {
    m_mut.lock();
    
    if (m_tasks[id].finished)
    {
      result_set = true;
      result = m_tasks[id].result;
    }
    else
    {
      result_set = false;
    }
    
    m_mut.unlock();
    
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
  communication::Task_Param const & a = args.a();
  communication::Task_Param const & b = args.b();
  communication::Task_Param const & p = args.p();
  communication::Task_Param const & m = args.m();
  
  int64_t a_value = try_get_param(a);
  int64_t b_value = try_get_param(b);
  int64_t p_value = try_get_param(p);
  int64_t m_value = try_get_param(m);
  
  work(self_id, a_value, b_value, p_value, m_value, args.n());
}

void Worker::work(int self_id, int64_t a, int64_t b, int64_t p, int64_t m, int64_t n)
{
  while (n-- > 0)
  {
    b = (a * p + b) % m;
    a = b;
  }
    
  int64_t res = a;
  
  m_mut.lock();
  
  Task & self_task = m_tasks[self_id];
  self_task.result = res;
  self_task.finished = true;
  
  self_task.cv->notify_all();
  delete self_task.cv;
  self_task.cv = nullptr;
  
  if (m_consumer_func)
  {
    m_consumer_func(self_id, self_task.request_id, res);
  }
  
  m_mut.unlock();
}

int64_t Worker::try_get_param(communication::Task_Param const & param)
{
  int64_t res = 0;
  
  if (param.has_dependenttaskid())
  {
    int param_id = param.dependenttaskid();
    
    std::unique_lock<std::mutex> lck(m_mut);
    
    if (!m_tasks[param_id].finished)
    {
      m_tasks[param_id].cv->wait(lck);
    }
    else
    {
      lck.unlock();
    }
    
    m_mut.lock();
    
    res = m_tasks[param.dependenttaskid()].result;
    
    m_mut.unlock();
  }
  else
  {
    res = param.value();
  }
  
  return res;
}
