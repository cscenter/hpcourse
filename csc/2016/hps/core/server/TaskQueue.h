#ifndef __SERVER_TASK_QUEUE__
#define __SERVER_TASK_QUEUE__

#include <boost/thread/recursive_mutex.hpp>
#include <mutex>
#include <queue>
#include <condition_variable>
#include <utility>
#include "../metadata/protocol.pb.h"

class AsyncService;
class TaskQueue {
public:
    TaskQueue(std::condition_variable & main_threads); 
    void add_message(const communication::WrapperMessage & message, const boost::shared_ptr<AsyncService> & service);
    std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>> get_task(); 
    size_t size();

private:
    std::queue<std::pair<communication::WrapperMessage, const boost::shared_ptr<AsyncService>>> queue_;
    boost::recursive_mutex                      mutex_;
    std::condition_variable                   & main_threads_;
};

#endif //__SERVER_TASK_QUEUE__
