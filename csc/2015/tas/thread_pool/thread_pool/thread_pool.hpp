#ifndef THREAD_POOL_HPP_INCLUDED
#define THREAD_POOL_HPP_INCLUDED

#include <functional>
#include <thread>
#include <atomic>
#include <vector>
#include <memory>
#include <exception>
#include <future>
#include <mutex>
#include <queue>

namespace std_utils
{
    // thread pool to run user's functors with signature
    //      ret func(int id, other_params)
    // where id is the index of the thread that runs the functor
    // ret is some return type
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

        // get the number of running threads in the pool
        int size();

        // number of idle threads
        int n_idle();
        std::thread & get_thread(int i);

        void resize(int nThreads);

        void clear_queue();

        std::function<void(int)> pop();

        void stop(bool isWait = false);

        std::thread::id std_utils::thread_pool::get_id(int i);

        template<typename F, typename... Rest>
        auto push(F && f, Rest&&... rest) ->std::future<decltype(f(0, rest...))> {
            auto pck = std::make_shared<std::packaged_task<decltype(f(0, rest...))(int)>>(
                std::bind(std::forward<F>(f), std::placeholders::_1, std::forward<Rest>(rest)...)
                );

            this->q.push([pck](int id) {
                (*pck)(id);
            });

            std::unique_lock<std::mutex> lock(this->mutex);
            this->cv.notify_one();

            return pck->get_future();
        }

        template<typename F>
        auto push(F && f) ->std::future<decltype(f(0))> {
            auto pck = std::make_shared<std::packaged_task<decltype(f(0))(int)>>(std::forward<F>(f));

            this->q.push([pck](int id) {
                (*pck)(id);
            });

            std::unique_lock<std::mutex> lock(this->mutex);
            this->cv.notify_one();

            return pck->get_future();
        }

    private:
        void set_thread(int i);

        void init();

        std::vector<std::unique_ptr<std::thread>> threads;
        std::vector<std::shared_ptr<std::atomic<bool>>> flags;
        mutable std::queue<std::function<void(int id)>> q;
        std::atomic<bool> isDone;
        std::atomic<bool> isStop;
        std::atomic<int> nWaiting;  // how many threads are waiting

        std::mutex mutex;
        std::condition_variable cv;
    };

}

#endif
