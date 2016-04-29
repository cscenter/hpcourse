#include "task.hpp"
#include <iostream>

task::task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n, int64_t request_id, std::string client_id)
        : a_(a), b_(b), p_(p), m_(m), n_(n)
        , result_(-1)
        , request_id_(request_id)
        , client_id_(client_id)
        , cond_var_(new std::condition_variable) {}



// condition_variable не копируется, так что по идее только новую создавать надо
// если в конструекторе копии явно не присваивать result от from.result,
// то он неправильно инициализируется,
// а разве он не должен для примитивных типов их побитово копировать????


// ??????????????????????????????????????????????????????????????????????????????????????????????????




task::task(task const& from)
        : a_(from.a_), b_(from.b_), p_(from.p_), m_(from.m_), n_(from.n_)
        , result_(from.result_)
        , request_id_(from.request_id_)
        , client_id_(from.client_id_)
        , cond_var_(new std::condition_variable) {}

task::~task()
{
//    delete cond_var_;
}

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


