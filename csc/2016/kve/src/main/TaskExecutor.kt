package main

import java.net.Socket

class TaskExecutor(executorSize: Int) {
    private val DEFAULT_SIZE: Int = executorSize
    private val taskQueue = SynchronizedQueue<Task>()
    private val threadList = Array(DEFAULT_SIZE, { i ->
        Thread(Runnable {
            while (true) {
                val task = popTask()
                task.result = task.runFunction()
                task.sendResult()
            }
        })
    })
    private val taskResults = SynchronizedMap<Int, Task>()


    fun create() {
        for (thread in threadList) {
            thread.start()
        }
    }

    fun execute(task: Task) {
        taskResults.put(task.getId(), task)
        taskQueue.put(task)
    }

    fun popTask(): Task {
        return taskQueue.pop()
    }

    // sen error for invalid task id
    fun subscribeToResults(taskId: Int, requestId: Long, socket: Socket) {
        val task = taskResults.get(taskId)
        task?.subscribe(requestId, socket)
    }

    fun listTasks(): MutableCollection<Task>? {
        return taskResults.values
    }
}