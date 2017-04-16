#include "task.hpp"
#include <iostream>

task::task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n, int64_t request_id, std::string client_id)
        : a_(a), b_(b), p_(p), m_(m), n_(n)
        , result_(-1)
        , request_id_(request_id)
        , client_id_(client_id)
        , cond_var_(std::make_shared<std::condition_variable>()) {}

task::~task() {}

void task::calculate(std::mutex& task_mutex) {

    int64_t a_cp = a_,
            b_cp = b_,
            p_cp = p_,
            m_cp = m_,
            n_cp = n_;

    while (n_cp-- > 0)
    {
        b_cp = (a_cp * p_cp + b_cp) % m_cp;
        a_cp = b_cp;
    }

    std::unique_lock<std::mutex> task_lock(task_mutex);
    result_ = a_cp;

    cond_var_->notify_all();
}


