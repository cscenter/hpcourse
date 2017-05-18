package taskExecution

import collections.SynchronizedMap
import collections.SynchronizedQueue
import java.net.Socket

class TaskExecutor(executorSize: Int) {
    private val DEFAULT_SIZE: Int = executorSize
    private val taskQueue = SynchronizedQueue<Task>()
    private val taskResults = SynchronizedMap<Int, Task>()
    private val runningTasks = SynchronizedMap<Int, Any>()
    private val threadList = Array(DEFAULT_SIZE, { i ->
        Thread(Runnable {
            while (true) {
                val task = popTask()
                waitForDependenciesComplete(task)
                runningTasks.put(task.id, Any())
                task.result = task.runFunction(a=getParamValue(task, "a"), b=getParamValue(task, "b"), 
                                                m=getParamValue(task, "m"), p=getParamValue(task, "p"))
                task.sendResult()
                runningTasks.remove(task.id)
            }
        })
    })
    
    fun waitForDependenciesComplete(task: Task) {
        task.getDependenciesIds().forEach { 
            if (taskResults[it.value]?.result == null) {
                (runningTasks[it.value] as Object).wait()
            }
        }
    }
    
    fun getParamValue(task: Task, paramName: String): Long {
        val dependencyId = task.getDependenciesIds()[paramName]
        when (paramName) {
            "a" -> return if (dependencyId != null) taskResults[dependencyId]?.result!! else task.task.a.value
            "b" -> return if (dependencyId != null) taskResults[dependencyId]?.result!! else task.task.b.value
            "m" -> return if (dependencyId != null) taskResults[dependencyId]?.result!! else task.task.m.value
            "p" -> return if (dependencyId != null) taskResults[dependencyId]?.result!! else task.task.p.value
        }
        return 1        
    }


    fun create() {
        threadList.forEach { it.start() } 
    }

    fun execute(task: Task) {
        taskResults.put(task.id, task)
        taskQueue.put(task)
    }

    fun popTask(): Task {
        return taskQueue.pop()
    }

    // TODO: set error for invalid task id
    fun subscribeToResults(taskId: Int, requestId: Long, socket: Socket) {
        val task = taskResults[taskId]
        task?.subscribe(requestId, socket)
    }

    val listTasks: Collection<Task>
        get() = taskResults.values
}