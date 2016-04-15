#include "task.hpp"

task::task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n)
        : result_(-1), request_id_(-1), a_(a), b_(b), p_(p), m_(m), n_(n)
        ,cond_var_(new std::condition_variable)
        {}

task::~task() { delete cond_var_; }

void task::calculate(std::mutex& task_mutex) {

    while (n_-- > 0) {
        b_ = (a_ * p_ + b_) % m_;
        a_ = b_;
    }

    std::unique_lock<std::mutex> task_lock(task_mutex);
    result_ = a_;
    cond_var_->notify_all();
}
