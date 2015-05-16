#include "thread_pool.hpp"

std_utils::thread_pool::thread_pool()
{
    init();
}

std_utils::thread_pool::thread_pool(int nThreads)
{
    init();
    resize(nThreads);
}

std_utils::thread_pool::~thread_pool()
{
    stop(true);
}

int std_utils::thread_pool::size()
{
    return static_cast<int>(this->threads.size());
}

int std_utils::thread_pool::n_idle()
{
    return this->nWaiting;
}

std::thread & std_utils::thread_pool::get_thread(int i)
{
    return *this->threads[i];
}

std::thread::id std_utils::thread_pool::get_id(int i)
{
    return threads[i]->get_id();
}

void std_utils::thread_pool::resize(int nThreads) {
    if (!this->isStop && !this->isDone) {
        int oldNThreads = static_cast<int>(this->threads.size());
        if (oldNThreads <= nThreads) {  // if the number of threads is increased
            this->threads.resize(nThreads);
            this->flags.resize(nThreads);

            for (int i = oldNThreads; i < nThreads; ++i) {
                this->flags[i] = std::make_shared<std::atomic<bool>>(false);
                this->set_thread(i);
            }
        }
        else {  // the number of threads is decreased
            for (int i = oldNThreads - 1; i >= nThreads; --i) {
                *this->flags[i] = true;  // this thread will finish
                this->threads[i]->detach();
            }
            {
                // stop the detached threads that were waiting
                std::unique_lock<std::mutex> lock(this->mutex);
                this->cv.notify_all();
            }
            this->threads.resize(nThreads);  // safe to delete because the threads are detached
            this->flags.resize(nThreads);  // safe to delete because the threads have copies of shared_ptr of the flags, not originals
        }
    }
}

void std_utils::thread_pool::clear_queue() {
    std::unique_lock<std::mutex> lock(this->mutex);
    while (!this->q.empty())
        this->q.pop();  // empty the queue
}

std::function<void(int)> std_utils::thread_pool::pop() {
    std::function<void(int)> f;
    std::unique_lock<std::mutex> lock(this->mutex);
    if (!this->q.empty()) {
        f = this->q.front();
        this->q.pop();
    }
    return f;
}

void std_utils::thread_pool::stop(bool isWait) {
    if (!isWait) {
        if (this->isStop)
            return;
        this->isStop = true;
        for (int i = 0, n = this->size(); i < n; ++i) {
            *this->flags[i] = true;  // command the threads to stop
        }
        this->clear_queue();  // empty the queue
    }
    else {
        if (this->isDone || this->isStop)
            return;
        this->isDone = true;  // give the waiting threads a command to finish
    }
    {
        std::unique_lock<std::mutex> lock(this->mutex);
        this->cv.notify_all();  // stop all waiting threads
    }
    for (int i = 0; i < static_cast<int>(this->threads.size()); ++i) {  // wait for the computing threads to finish
        if (this->threads[i]->joinable())
            this->threads[i]->join();
    }
    // if there were no threads in the pool but some functors in the queue, the functors are not deleted by the threads
    // therefore delete them here
    this->clear_queue();
    this->threads.clear();
    this->flags.clear();
}

void std_utils::thread_pool::set_thread(int i) {
    std::shared_ptr<std::atomic<bool>> flag(this->flags[i]);  // a copy of the shared ptr to the flag
    auto f = [this, i, flag/* a copy of the shared ptr to the flag */]() {
        std::atomic<bool> & _flag = *flag;
        std::function<void(int id)> _f;
        bool isPopped = false;
        while (true) {
            while (true) {  // if there is anything in the queue
                if (!isPopped) {
                    std::unique_lock<std::mutex> lock(this->mutex);
                    if (this->q.empty())
                        break;
                    _f = this->q.front();
                    this->q.pop();
                }
                _f(i);
                isPopped = false;

                if (_flag)
                    return;  // the thread is wanted to stop, return even if the queue is not empty yet
            }

            // the queue is empty here, wait for the next command
            std::unique_lock<std::mutex> lock(this->mutex);
            ++this->nWaiting;
            this->cv.wait(lock, [this, &_f, &isPopped, &_flag](){
                isPopped = !this->q.empty();
                if (isPopped) {
                    _f = this->q.front();
                    this->q.pop();
                }
                return isPopped || this->isDone || _flag;
            });
            --this->nWaiting;

            if (!isPopped)
                return;  // if the queue is empty and this->isDone == true or *flag then return
        }
    };
    this->threads[i].reset(new std::thread(f));  // compiler may not support std::make_unique()
}

void std_utils::thread_pool::init()
{
    this->nWaiting = 0;
    this->isStop = false;
    this->isDone = false;
}

