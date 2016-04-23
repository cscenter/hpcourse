#ifndef THREAD_POOL_H
#define THREAD_POOL_H

#include <boost/thread.hpp>
#include <functional>
#include <atomic>
#include <unordered_map>
#include <queue>

#include "protocol.pb.h"

class thread_pool
{
public:

  thread_pool();

  void thread_pool::put_command(const communication::ServerRequest& command
                                , std::function<void(const communication::ServerResponse&)> callback);

private:
  void run();

  int64_t run_task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n);

  bool check_request(const communication::ServerRequest& request);

  std::vector<boost::thread> _threads;
  std::atomic<unsigned int> _uniq_id;

  boost::mutex _new_tasks_sync;
  boost::condition_variable _new_tasks_cv;
  std::queue<std::pair<communication::ServerRequest
                       , std::function<void(const communication::ServerResponse&)>>> _new_tasks;

  boost::mutex _tasks_sync;
  boost::condition_variable _solved_tasks_cv;
  std::unordered_map<int, communication::ListTasksResponse_TaskDescription> _tasks;

  thread_pool(const thread_pool&) = delete;
};

#endif
