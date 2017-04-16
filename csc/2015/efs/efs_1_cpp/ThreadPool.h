#ifndef CSC2015THREADPOOL_THREADPOOL_H
#define CSC2015THREADPOOL_THREADPOOL_H

#include <functional>
#include <vector>
#include <thread>
#include <list>
#include <mutex>
#include <atomic>
#include <condition_variable>

enum class TaskState {
    QUEUED,
    RUNNING,
    EXCEPTION_THROWN,
    FINISHED,
    CANCELLED
};
struct TaskResult {
    TaskState state;
    int returned;
    std::exception_ptr exception;
};

class Task;
typedef std::shared_ptr<Task> PTask;

class ThreadPool;

typedef std::function<int(Task &currentTask)> TaskHandler;

class Task {
public:
    ~Task();

    Task(Task &&) = delete;
    Task(const Task &) = delete;
    Task& operator=(const Task &) = delete;
    Task& operator=(Task &&) = delete;

    void interrupt();
    bool interrupted() const;
    TaskState state() const;
    TaskResult wait();

private:
    friend class ThreadPool;

    Task(ThreadPool &pool, TaskHandler &&handler);

    ThreadPool &pool;
    TaskHandler handler;

    std::list<PTask>::iterator inQueue;

    std::mutex finishedChangedMutex;
    std::condition_variable finishedChanged;

    std::atomic<bool> _interrupted;
    std::atomic<TaskState> _state;
    std::atomic<int> _returned;
    std::exception_ptr _exception;

    void setState(TaskState state);
    void setReturnedValue(int value);
    void setException(std::exception_ptr exception);
};

class ThreadPool {
public:
    ThreadPool(int threadsCount);
    ~ThreadPool();

    PTask submit(TaskHandler task);
    size_t size() const;

private:
    friend class Task;

    void work(Task &task);
    void threadWork();
    PTask grabTask();
    PTask grabTask(Task &waitForThat);

    std::vector<std::thread> threads;
    std::atomic<bool> isTerminating;

    std::mutex tasksMutex;
    std::condition_variable grabTaskCondition;
    std::list<PTask> queue;
};

#endif //CSC2015THREADPOOL_THREADPOOL_H
