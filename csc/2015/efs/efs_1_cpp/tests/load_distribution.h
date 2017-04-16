#ifndef CSC2015THREADPOOL_SIMPLE_H
#define CSC2015THREADPOOL_SIMPLE_H

#include <cstdio>
#include <map>
#include "../ThreadPool.h"

class LoadDistributionTest {
public:
    static const char *name(int THREADS) {
        static char buf[100];
        snprintf(buf, sizeof buf, "Load distribution test for %d threads", THREADS);
        return buf;
    }

public:
    LoadDistributionTest(int THREADS) : pool(THREADS) {}

private:
    static const int TOTAL = int(1e4);

public:
    bool run() {
        std::vector<PTask> tasks(TOTAL);
        std::vector<std::thread::id> ids(TOTAL);
        for (int i = 0; i < TOTAL; i++) {
            tasks[i] = pool.submit([this, &ids, i](Task &) {
                ids[i] = std::this_thread::get_id();
                return 0;
            });
            tasks[i]->wait();
        }

        std::map<std::thread::id, int> counts;
        for (auto x : ids) counts[x]++;

        ensure(counts.size() == pool.size());
        ensure(counts.count(std::thread::id()) == 0);
        for (auto x : counts) {
            ensure(x.second >= TOTAL / (int)pool.size() / 3);
        }
        return true;
    }

private:
    ThreadPool pool;
};

#endif //CSC2015THREADPOOL_SIMPLE_H
