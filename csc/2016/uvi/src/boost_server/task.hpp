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
    task(task const& from);
    ~task();
    void calculate(std::mutex& task_mutex);

    inline void set_client_id(std::string client_id) { client_id_ = client_id; }
    inline void set_request_id(int64_t request_id)   { request_id_ = request_id; }
    inline void set_result(int64_t result)           { result_ = result; }

    inline int64_t set_a(int64_t a) { a_ = a; }
    inline int64_t set_b(int64_t b) { b_ = b; }
    inline int64_t set_p(int64_t p) { p_ = p; }
    inline int64_t set_m(int64_t m) { m_ = m; }
    inline int64_t set_n(int64_t n) { n_ = n; }

    inline std::condition_variable* get_cond_var_ptr() const {
        return cond_var_.get();
        //return cond_var_;
    }

    inline std::string get_client_id()  { return client_id_; }
    inline int64_t     get_request_id() { return request_id_; }
    inline int64_t get_result() const { return result_; }

    inline int64_t get_a() const { return a_; }
    inline int64_t get_b() const { return b_; }
    inline int64_t get_p() const { return p_; }
    inline int64_t get_m() const { return m_; }
    inline int64_t get_n() const { return n_; }

private:
    int64_t                  a_, b_, p_, m_, n_;
    int64_t                  result_;
    int64_t                  request_id_;
    std::string              client_id_;
    std::unique_ptr<std::condition_variable> cond_var_;
    //std::condition_variable* cond_var_;
};

#endif //PARALLEL_TASK_HPP
