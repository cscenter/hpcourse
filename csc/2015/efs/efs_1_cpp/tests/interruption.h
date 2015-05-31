#ifndef CSC2015THREADPOOL_INTERRUPTION_H
#define CSC2015THREADPOOL_INTERRUPTION_H

#include "../ThreadPool.h"

class InterruptionTest {
public:
    static const char *name() {
        return "Interruption test for two threads and four tasks";
    }

public:
    InterruptionTest() : pool(2) {}

private:
    static const int INTERRUPTED_VALUE = 0x0000DEAD;
    static int doWork(Task &self, int n, std::atomic<int> &counter, const std::vector<PTask> &toInterrupt) {
        unsigned sum = 0;
        // some weird function which calculates in O(n^2)
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                sum += (i + j) / 2;
                counter++;
            }
            if (self.interrupted()) { return INTERRUPTED_VALUE; }
        }
        for (size_t i = 0; i < toInterrupt.size(); i++)
            if (i != 2) {
                toInterrupt[i]->interrupt();
            }
        return sum;
    }

public:
    bool run() {
        /*
         * First and third tasks calculate same value
         * Second and fourth need significantly more time
         * First task interrupts all other (except the third) after it finishes calculations
         * As we have two threads, we except tasks 2 and 4 to be interrupted\
         * Moreover, task 4 should be interrupted without running
         */

        std::atomic<int> counters[4] = {};
        std::vector<PTask> tasks;
        tasks.push_back(pool.submit([&counters, &tasks](Task &self) { return doWork(self, 1e4, counters[0], tasks); }));
        tasks.push_back(pool.submit([&counters, &tasks](Task &self) { return doWork(self, 2e4, counters[1], tasks); }));
        tasks.push_back(pool.submit([&counters, &tasks](Task &self) { return doWork(self, 1e4, counters[2], tasks); }));
        tasks.push_back(pool.submit([&counters, &tasks](Task &self) { return doWork(self, 2e4, counters[3], tasks); }));

        TaskResult results[4];
        for (int i = 0; i < 4; i++) {
            results[i] = tasks[i]->wait();
        }
        ensure(counters[0] == int(1e8));
        ensure(counters[1] <= int(1.5e8));
        ensure(counters[2] == int(1e8));
        ensure(counters[3] == 0);

        for (int i = 0; i < 3; i++) {
            ensure(results[i].state == TaskState::FINISHED);
        }
        ensure(results[3].state == TaskState::CANCELLED);

        ensure(results[0].returned == results[2].returned);
        ensure(results[1].returned == INTERRUPTED_VALUE);
        return true;
    }

private:
    ThreadPool pool;
};

#endif //CSC2015THREADPOOL_INTERRUPTION_H
