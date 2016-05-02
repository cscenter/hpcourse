#ifndef PARALLEL_TASK_HPP
#define PARALLEL_TASK_HPP

#include <mutex>
#include <string>
#include <condition_variable>
#include <memory>
#include "protocol.pb.h"

struct task {

public:
    task(int64_t a, int64_t b, int64_t p, int64_t m, int64_t n, int64_t request_id, std::string client_id);
    ~task();
    void calculate(std::mutex& task_mutex);

    inline std::condition_variable* get_cond_var_ptr() const
    {
        return cond_var_.get();
    }

    inline std::string get_client_id()  { return client_id_; }
    inline int64_t     get_result() const { return result_; }

    inline int64_t get_a() const { return a_; }
    inline int64_t get_b() const { return b_; }
    inline int64_t get_p() const { return p_; }
    inline int64_t get_m() const { return m_; }
    inline int64_t get_n() const { return n_; }

private:
    int64_t a_, b_, p_, m_, n_;
    int64_t result_;
    int64_t request_id_;
    std::string client_id_;
    std::shared_ptr<std::condition_variable> cond_var_;
};

#endif //PARALLEL_TASK_HPP
