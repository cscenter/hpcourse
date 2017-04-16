#ifndef CSC2015THREADPOOL_MERGESORT_H
#define CSC2015THREADPOOL_MERGESORT_H

#include <cstdio>
#include <random>
#include <algorithm>
#include "tests.h"
#include "../ThreadPool.h"

class MergeSortTest {
public:
    static const char *name(int LENGTH, int THREADS) {
        static char buf[128];
        snprintf(buf, sizeof buf, "Merge sort for %d thread(s), array length is %d", THREADS, LENGTH);
        return buf;
    }

private:
    std::vector<int> data;

    int mergeSort(int l, int r, Task &task) {
        if (r - l + 1 <= 10000) {
            sort(data.begin() + l, data.begin() + r + 1);
            return 0;
        }
        int m = (l + r) / 2;
        if (task.interrupted()) throw 0;
        PTask a = pool.submit([this, l, m](Task &_task) { return mergeSort(l, m, _task); });
        PTask b = pool.submit([this, m, r](Task &_task) { return mergeSort(m + 1, r, _task); });
        if (a->wait().state != TaskState::FINISHED) throw 0;
        if (b->wait().state != TaskState::FINISHED) throw 0;
        if (task.interrupted()) throw 0;
        std::inplace_merge(data.begin() + l, data.begin() + m + 1, data.begin() + r + 1);
        return 0;
    }

public:
    MergeSortTest(int LENGTH, int THREADS) : data(LENGTH), pool(THREADS) {
        std::mt19937 gen;
        std::uniform_int_distribution<int> distrib;
        for (size_t i = 0; i < data.size(); i++) {
            data[i] = distrib(gen);
        }
    }

    bool run() {
        PTask task = pool.submit([this](Task &_task) { return mergeSort(0, data.size() - 1, _task); });
        ensure(task->wait().state == TaskState::FINISHED);
        for (size_t i = 0; i + 1 < data.size(); i++) {
            ensure(data[i] <= data[i + 1]);
        }

        return true;
    }

private:
    ThreadPool pool;
};

#endif //CSC2015THREADPOOL_MERGESORT_H

