#include "ThreadPool.h"

using std::lock_guard;
using std::unique_lock;
using std::mutex;

ThreadPool::ThreadPool(int threadsCount) : isTerminating(false) {
    for (int i = 0; i < threadsCount; i++) {
        threads.emplace_back(&ThreadPool::threadWork, this);
    }
}

PTask ThreadPool::submit(TaskHandler handler) {
    // not make_shared because Task constructor is private
    PTask task(new Task(*this, std::move(handler)));
    lock_guard<mutex> lock(tasksMutex);
    queue.push_back(task);
    task->inQueue = --queue.end();
    grabTaskCondition.notify_one();
    return task;
}
ThreadPool::~ThreadPool() {
    isTerminating = true;
    {
        unique_lock<mutex> lock(tasksMutex);
        grabTaskCondition.notify_all();
    }
    for (auto &t : threads) {
        t.join();
    }
}

size_t ThreadPool::size() const {
    return threads.size();
}

PTask ThreadPool::grabTask() {
    unique_lock<mutex> lock(tasksMutex);
    for (;;) {
        grabTaskCondition.wait(lock, [this]() { return isTerminating || !queue.empty(); });
        if (isTerminating) { return nullptr; }
        PTask result = queue.front();
        result->inQueue = std::list<PTask>::iterator();
        queue.pop_front();
        return result;
    }
}
PTask ThreadPool::grabTask(Task &waitForThat) {
    unique_lock<mutex> lock(tasksMutex);
    if (waitForThat.state() == TaskState::QUEUED) {
        PTask pointer = *waitForThat.inQueue;
        queue.erase(waitForThat.inQueue);
        waitForThat.inQueue = std::list<PTask>::iterator();
        return pointer;
    }
    for (;;) {
        grabTaskCondition.wait(lock, [this, &waitForThat]() {
            return isTerminating || !queue.empty() || waitForThat.state() > TaskState::RUNNING;
        });
        if (isTerminating) { return nullptr; }
        if (waitForThat.state() > TaskState::RUNNING) { return nullptr; }
        PTask result = queue.front();
        result->inQueue = std::list<PTask>::iterator();
        queue.pop_front();
        return result;
    }
}

thread_local ThreadPool *currentThreadPool = nullptr;

void ThreadPool::work(Task &task) {
    if (task.interrupted()) {
        task.setState(TaskState::CANCELLED);
    } else {
        try {
            task.setState(TaskState::RUNNING);
            task.setReturnedValue(task.handler(task));
        } catch (...) {
            task.setException(std::current_exception());
        }
    }

    // For those who're waiting for tasks inside wait()
    // Probably we're the task they are waiting for?
    unique_lock<mutex> lock(tasksMutex);
    grabTaskCondition.notify_all();
}

void ThreadPool::threadWork() {
    currentThreadPool = this;
    for (;;) {
        PTask task = grabTask();
        if (!task) { // terminating
            break;
        }
        work(*task);
    }
}

Task::Task(ThreadPool &_pool, TaskHandler &&_handler) : pool(_pool), handler(std::move(_handler)), _interrupted(false), _state(TaskState::QUEUED), _returned(0xDEADBEEF), _exception() {
}
Task::~Task() {
}

void Task::interrupt() {
    _interrupted = true;
}
bool Task::interrupted() const {
    return _interrupted;
}
TaskState Task::state() const {
    return _state;
}

void Task::setState(TaskState newState) {
    _state = newState;
    if (newState > TaskState::RUNNING) {
        unique_lock<mutex> lock(finishedChangedMutex);
        finishedChanged.notify_all();
    }
}
void Task::setReturnedValue(int value) {
    _returned = value;
    // use store instead of operator= to make sure that '_returned' change is visible in another threads
    _state.store(TaskState::FINISHED, std::memory_order_release);
    unique_lock<mutex> lock(finishedChangedMutex);
    finishedChanged.notify_all();
}
void Task::setException(std::exception_ptr exception) {
    _exception = exception;
    // use store instead of operator= to make sure that '_returned' change is visible in another threads
    _state.store(TaskState::EXCEPTION_THROWN, std::memory_order_release);
    unique_lock<mutex> lock(finishedChangedMutex);
    finishedChanged.notify_all();
}
TaskResult Task::wait() {
    if (currentThreadPool) {
        while (state() <= TaskState::RUNNING) {
            PTask task = currentThreadPool->grabTask(*this);
            if (!task) { // terminating
                break;
            }
            currentThreadPool->work(*task);
        }
    }
    unique_lock<mutex> lock(finishedChangedMutex);
    finishedChanged.wait(lock, [this]() {
        return _state.load(std::memory_order_acquire) > TaskState::RUNNING;
    });
    TaskResult res  = { _state, _returned, _exception };
    return res;
};
