#include "thread_pool.h"

#include <cmath>

#include <gtest/gtest.h>

TEST(ThreadPool, SimpleTask)
{
    thread_pool pool(1);
    auto result = pool.submit([](){return 2 + 2;});
    ASSERT_EQ(result.get(), 4);
}

TEST(ThreadPool, ShouldRethrow)
{
    thread_pool pool(1);
    class my_exception : public std::exception {
        using std::exception::exception;
    };

    auto task = [](){throw my_exception();};
    auto fut = pool.submit(task);

    ASSERT_THROW(fut.get(), my_exception);
}

TEST(ThreadPool, ShouldRunSelfTasks)
{
    thread_pool pool(1);

    auto task = [&]() {
        int a = 2;
        auto self_task = [&](){
            a += 2;
        };
        pool.submit(self_task).wait();
        return a;
    };

    ASSERT_EQ(pool.submit(task).get(), 4);
}

TEST(ThreadPool, ManySimpleTasks)
{
    thread_pool pool(5);

    const size_t N = 10000;
    std::vector<int> answers(N);

    for (size_t i = 0; i < N; ++i)
        answers[i] = i * i;

    auto task = [&](size_t l, size_t r){
      for (size_t i = l; i < r; ++i)
          answers[i] -= std::pow(i, 2);
    };

    const int task_count = 200;
    const int offset = N / task_count;

    std::vector<future<void>> futures;
    for (int i = 0; i < task_count; ++i)
    {
        futures.push_back(pool.submit(task, i * offset, i * offset + offset));
    }

    for (auto& fut : futures) {
        fut.wait();
    }

    ASSERT_TRUE(std::all_of(answers.begin(), answers.end(),
                            [](int a){return a == 0;}));

}

int main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
