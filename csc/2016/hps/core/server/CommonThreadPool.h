#ifndef __COMMON_THREAD_POOL__
#define __COMMON_THREAD_POOL__

#include <cstdio>
#include <condition_variable>
#include <thread>
#include "TaskQueue.h"
#include "RunningAndDoneQueue.h"

class CommonThreadPool {
public:
   CommonThreadPool(size_t size, TaskQueue & task_queue, RunningAndDoneQueue & results, std::condition_variable & threads_notify_cond_var);     
   void set_calculation_function(int64_t (*task)(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n));
private:
    void run();
    void task();
    void process_subscriber(int64_t task_id, int64_t result,
                                          const std::pair<std::vector<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>>, std::vector<int64_t>>  & subscribers);

private:
    int64_t (*calculation_function_)(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n);
    std::vector<std::thread> pool_threads_;
    size_t size_;
    TaskQueue & task_queue_;
    RunningAndDoneQueue & results_;
    std::condition_variable & threads_notify_cond_var_;
};

#endif
