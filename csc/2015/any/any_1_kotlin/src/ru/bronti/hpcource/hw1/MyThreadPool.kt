package ru.bronti.hpcource.hw1

import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException

public class MyThreadPool(public val size: Int) {

    public volatile var isShut: Boolean = false
        set(value) {
            synchronized(queueMonitor) {
                synchronized(emptynessMonitor) {
                    $isShut = value
                    emptynessMonitor.notifyAll()
                    queueMonitor.notifyAll()
                }
            }
        }

    private volatile var isClosed = false

    private val queueMonitor = Object()
    private val emptynessMonitor = Object()

    val queue: Queue<MyFuture<*>> = LinkedList()
    val pool = Array(size, { Thread(MyWorker("Worker " + it)) })

    init {
        for (thread in pool) {
            thread.start()
        }
    }

    public fun submit<V>(task: Callable<V>, name: String = "_noname_"): MyFuture<V>? {
        if (isClosed) {
            throw RejectedExecutionException()
        }
        val result = MyFuture(task, name)
        synchronized(queueMonitor) {
            queue.add(result)
            queueMonitor.notifyAll()
        }
        return result
    }

    public fun shutdown() {
        isClosed = true
        synchronized(emptynessMonitor) {
            while (!queue.isEmpty() && !isShut) {
                emptynessMonitor.wait()
            }
        }
        isShut = true
    }

    public fun shutdownNow(): List<Callable<*>>? {
        isClosed = true
        isShut = true
        for (thread in pool) {
            //thread.join()
            thread.interrupt()
        }
        return queue map { it.getTaskAndCancel() }
    }

    private inner class MyWorker(val name: String) : Runnable {

        fun getTask(): MyFuture<*>? {
            var task: MyFuture<*>?
            do {
                task = synchronized(queueMonitor) {
                    while (queue.isEmpty() && !isShut) {
                        try {
                            queueMonitor.wait()
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                    synchronized(emptynessMonitor) {
                        emptynessMonitor.notifyAll()
                        queue.poll()
                    }
                }
            } while ((task != null) && (task!!.isCancelled()))
            return task
        }

        override fun run() {
            while (!isShut) {
                val task = getTask()
                if (task == null || Thread.currentThread().isInterrupted()) {
                    break
                }
                try {
                    //println(name + " captured " + task.name)
                    task.run(Thread.currentThread())
                    //println(name + " completed " + task.name)
                    Thread.interrupted()
                } catch (e: InterruptedException) {
                    //println("thrown exeption by " + task.name)
                    //do nothing
                }
            }
        }
    }

}