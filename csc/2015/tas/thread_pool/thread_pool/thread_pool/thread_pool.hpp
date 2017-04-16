#ifndef THREAD_POOL_HPP_INCLUDED
#define THREAD_POOL_HPP_INCLUDED

#include <vector>
#include <queue>

#include <memory>
#include <functional>

#include <atomic>
#include <mutex>
#include <future>
#include <boost/thread/thread.hpp>

namespace std_utils
{
    class thread_pool
    {
    public:
        thread_pool();

        explicit thread_pool(int nThreads);

        thread_pool(const thread_pool &) = delete;

        thread_pool(thread_pool &&) = delete;

        thread_pool & operator=(const thread_pool &) = delete;

        thread_pool & operator=(thread_pool &&) = delete;

        ~thread_pool();

        int size();

        int n_idle();

        boost::thread & get_thread(int id);

        void resize(int nThreads);

        void clear_queue();

        std::function<void(int)> pop();

        void stop(bool isWait = false);

        boost::thread::id get_id(int id);

        void interrupt(int id);

        template<typename F, typename ... Rest>
        auto submit(F && f, Rest && ... rest) -> std::future<decltype(f(0, rest ...))> {
            auto pck = std::make_shared<std::packaged_task<decltype(f(0, rest ...))(int)>>(
                std::bind(std::forward<F>(f), std::placeholders::_1, std::forward<Rest>(rest) ...)
                );

            q_.push([pck](int id) {
                (*pck)(id);
            });

            std::unique_lock<std::mutex> lock(mutex_);
            cv_.notify_one();

            return pck->get_future();
        }

        template<typename F>
        auto submit(F && f) -> std::future<decltype(f(0))> {
            auto pck = std::make_shared<std::packaged_task<decltype(f(0))(int)>>(std::forward<F>(f));

            q_.push([pck](int id) {
                (*pck)(id);
            });

            std::unique_lock<std::mutex> lock(mutex_);
            cv_.notify_one();

            return pck->get_future();
        }

    private:
        void set_thread(int i);

        void init();

        std::vector<std::unique_ptr<boost::thread>> threads_;
        std::vector<std::shared_ptr<std::atomic<bool>>> flags_;
        mutable std::queue<std::function<void(int id)>> q_;
        std::atomic<bool> isDone_;
        std::atomic<bool> isStop_;
        std::atomic<int> nWaiting_;

        std::mutex mutex_;
        std::condition_variable cv_;
    };
}

#endif