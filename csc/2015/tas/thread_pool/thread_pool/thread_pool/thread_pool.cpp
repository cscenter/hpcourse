#include "thread_pool.hpp"

std_utils::thread_pool::thread_pool() {
    init();
}

std_utils::thread_pool::thread_pool(int nThreads) {
    init();
    resize(nThreads);
}

std_utils::thread_pool::~thread_pool() {
    stop(true);
}

int std_utils::thread_pool::size() {
    return static_cast<int>(threads_.size());
}

int std_utils::thread_pool::n_idle() {
    return nWaiting_;
}

boost::thread & std_utils::thread_pool::get_thread(int id) {
    return *threads_.at(id);
}

boost::thread::id std_utils::thread_pool::get_id(int id) {
    return threads_.at(id)->get_id();
}

void std_utils::thread_pool::interrupt(int id) {
    threads_.at(id)->interrupt();
}

void std_utils::thread_pool::resize(int nThreads) {
    if (!isStop_ && !isDone_) {
        auto oldNThreads = static_cast<int>(threads_.size());
        if (oldNThreads <= nThreads) {
            threads_.resize(nThreads);
            flags_.resize(nThreads);

            for (auto i = oldNThreads; i < nThreads; ++i) {
                flags_[i] = std::make_shared<std::atomic<bool>>(false);
                set_thread(i);
            }
        } else {
            for (auto i = oldNThreads - 1; i >= nThreads; --i) {
                *flags_[i] = true;
                threads_[i]->detach();
            }
            {
                std::unique_lock<std::mutex> lock(mutex_);
                cv_.notify_all();
            }

            threads_.resize(nThreads);
            flags_.resize(nThreads);
        }
    }
}

void std_utils::thread_pool::clear_queue() {
    std::unique_lock<std::mutex> lock(mutex_);
    while (!q_.empty()) {
        q_.pop();
    }
}

std::function<void(int)> std_utils::thread_pool::pop() {
    std::function<void(int)> f;
    std::unique_lock<std::mutex> lock(mutex_);
    if (!q_.empty()) {
        f = q_.front();
        q_.pop();
    }

    return f;
}

void std_utils::thread_pool::stop(bool isWait) {
    if (!isWait) {
        if (isStop_)
            return;

        isStop_ = true;
        for (auto i = 0, n = size(); i < n; ++i) {
            *flags_[i] = true;
        }

        clear_queue();
    } else {
        if (isDone_ || isStop_)
            return;

        isDone_ = true;
    }

    {
        std::unique_lock<std::mutex> lock(mutex_);
        cv_.notify_all();
    }

    for (auto i = 0, size = static_cast<int>(threads_.size()); i < size; ++i) {
        if (threads_[i]->joinable())
            threads_[i]->join();
    }

    clear_queue();
    threads_.clear();
    flags_.clear();
}

void std_utils::thread_pool::set_thread(int i) {
    auto flag(flags_[i]);

    auto f = [this, i, flag]() {
        std::function<void(int id)> _f;
        auto & _flag = *flag;
        auto isPopped = false;

        while (true) {
            while (true) {
                if (!isPopped) {
                    std::unique_lock<std::mutex> lock(mutex_);
                    if (q_.empty())
                        break;
                    _f = q_.front();
                    q_.pop();
                }

                _f(i);
                isPopped = false;

                if (_flag)
                    return;
            }

            std::unique_lock<std::mutex> lock(mutex_);
            ++nWaiting_;

            cv_.wait(lock, [this, &_f, &isPopped, &_flag](){
                isPopped = !q_.empty();
                if (isPopped) {
                    _f = q_.front();
                    q_.pop();
                }

                return isPopped || isDone_ || _flag;
            });
            --nWaiting_;

            if (!isPopped)
                return;
        }
    };

    threads_[i].reset(new boost::thread(f));
}

void std_utils::thread_pool::init()
{
    nWaiting_ = 0;
    isStop_ = false;
    isDone_ = false;
}
