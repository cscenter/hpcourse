//
// Created by e.suvorov on 31.05.2015.
//

#ifndef CSC2015THREADPOOL_DFS_H
#define CSC2015THREADPOOL_DFS_H

#include <cstdio>
#include <vector>
#include <deque>
#include <random>
#include "../ThreadPool.h"

/*
 * This test generates random tree and runs recursive bfs, one task == one vertex
 * Some function is calculated recursively in each vertex to ensure correctness
 *
 * if WITH_EXCEPTIONS==false, then all values are passed with 'return' and exceptions are used for errors
 * if WITH_EXCEPTIONS==true, then all values are passed with 'throw unsigned' and correct returns indicate errors
 */

template<bool WITH_EXCEPTIONS>
class DfsTest {
public:
    static const char *name(int THREADS) {
        static char buf[100];
        snprintf(buf, sizeof buf, "Dfs for %d threads%s", THREADS, WITH_EXCEPTIONS ? " with exceptions" : "");
        return buf;
    }

public:
    DfsTest(int THREADS) : pool(THREADS) {
        std::mt19937 gen;
        std::uniform_int_distribution<int> valuesDistrib(1, 10);
        std::uniform_int_distribution<int> degreeDistrib(2, 3);
        const int MAX_VERTICES = int(0.4e6);

        // generate random tree with BFS
        children.push_back(std::vector<int>());
        for (size_t v = 0; v < children.size(); v++) {
            int degree = degreeDistrib(gen);
            for (int i = 0; i < degree && children.size() < MAX_VERTICES; i++) {
                children[v].push_back(children.size());
                children.push_back(std::vector<int>());
            }
        }
        // assign random values to nodes
        for (size_t v = 0; v < children.size(); v++) {
            values.push_back(valuesDistrib(gen));
        }
        answers.resize(children.size());
        calculateAnswers(0);
    }

private:
    std::vector<std::vector<int>> children;
    std::vector<unsigned> values, answers;

    unsigned calculateForVertex(const std::vector<unsigned> &childrenAnswers) {
        // calculate something in O(n^2)
        unsigned answer = 0;
        for (auto x : childrenAnswers)
            for (auto y : childrenAnswers)
                answer += x ^ y;
        return answer;
    }

    void calculateAnswers(int v) {
        answers[v] = values[v];
        std::vector<unsigned> current;
        for (int child : children[v]) {
            calculateAnswers(child);
            current.push_back(answers[child]);
        }
        answers[v] += calculateForVertex(current);
    }

    int calculateAnswersTask(int v) {
        unsigned result = values[v];
        std::vector<PTask> tasks;
        for (int child : children[v]) {
            tasks.push_back(pool.submit([this, child] (Task &) {
                return calculateAnswersTask(child);
            }));
        }
        std::vector<unsigned> current;
        for (auto x : tasks) {
            TaskResult taskResult = x->wait();
            if (WITH_EXCEPTIONS) {
                if (taskResult.state != TaskState::EXCEPTION_THROWN) return 0;
                try {
                    std::rethrow_exception(taskResult.exception);
                } catch (unsigned value) {
                    current.push_back(value);
                } catch (...) {
                    return 0;
                }
            } else {
                if (taskResult.state != TaskState::FINISHED) throw 0;
                current.push_back(taskResult.returned);
            }
        }
        result += calculateForVertex(current);
        if (result != answers[v]) {
            if (WITH_EXCEPTIONS) return 0;
            else throw 0;
        }
        if (WITH_EXCEPTIONS) throw result;
        return result;
    }

public:
    bool run() {
        TaskResult result =
                pool.submit([this](Task &) { return calculateAnswersTask(0); })->wait();
        if (WITH_EXCEPTIONS) {
            ensure(result.state == TaskState::EXCEPTION_THROWN);
        } else {
            ensure(result.state == TaskState::FINISHED);
        }
        return true;
    }

private:
    ThreadPool pool;
};

#endif //CSC2015THREADPOOL_DFS_H
