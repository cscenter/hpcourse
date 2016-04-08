#ifndef WORKER_H_
#define WORKER_H_

#include <mutex>
#include <condition_variable>
#include <vector>
#include <string>
#include <functional>

#include "protocol.pb.h"

struct Task
{
  unsigned int id;
  int64_t result;
  bool finished;
  std::string client_id;
  int64_t request_id;
  
  std::condition_variable * cv;

  communication::Task args;

  Task()
  : Task(0, communication::Task(), "No name", 0)
  { }

  Task(unsigned int task_id, communication::Task const & args_input, 
  std::string const & client_id_input, int64_t request_id_in)
  : id(task_id)
  , result(0)
  , finished(false)
  , client_id(client_id_input)
  , request_id(request_id_in)
  , cv(new std::condition_variable())
  , args(args_input)
  { }
};

class Worker
{
public:
  Worker()
  : Worker(nullptr)
  { }
  
  Worker(std::function<void(unsigned int, int64_t, int64_t)> c_func)
  : m_id(0)
  , m_consumer_func(c_func)
  { }
  
  ~Worker()
  {
    for (std::vector<Task>::iterator it = m_tasks.begin(); 
    it != m_tasks.end(); 
    ++it)
    {
      delete it->cv;
    }
  }
  
  unsigned int handle_submit_task(communication::SubmitTask const & submitTask, std::string const & client_id, int64_t request_id);
  void get_task_list(std::vector<Task> & out);
  bool subscribe(unsigned int id, bool & result_set, int64_t & result);
  
private:
  unsigned int add_task(communication::SubmitTask const & submitTask, std::string const & client_id, int64_t request_id);
  void start(unsigned int self_id, communication::Task args);
  void work(int self_id, int64_t a, int64_t b, int64_t p, int64_t m, int64_t n);
  int64_t try_get_param(communication::Task_Param const & param);
  
private:
  unsigned int m_id;
  std::mutex m_mut;
  std::vector<Task> m_tasks;
  std::function<void(unsigned int, int64_t, int64_t)> m_consumer_func;
};

#endif
