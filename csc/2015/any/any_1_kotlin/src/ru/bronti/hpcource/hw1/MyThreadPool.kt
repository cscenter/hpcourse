package ru.bronti.hpcource.hw1

import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.lang.ThreadGroup
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

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
    val pool = Array(size, { MyThread(MyWorker("Worker " + it)) })

    init {
        for (thread in pool) {
            thread.start()
        }
    }

    public fun submit<V>(task: Callable<V>, name: String = "_noname_"): MyFuture<V>? {
        if (isClosed) {
            throw RejectedExecutionException()
        }
        val result = MyFuture(task, name, this)
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
        for(t in pool) {
            t.interrupt()
        }
        return queue map { it.getTaskAndCancel() }
    }

    inner class MyWorker(val name: String) : Runnable {
        private fun getTask(mustHave: Boolean): MyFuture<*>? {
            var task: MyFuture<*>?
            do {
                task = synchronized(queueMonitor) {
                    while (queue.isEmpty() && !isShut && mustHave) {
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
            /*if (task != null) {
                println(Thread.currentThread().getName() + " caught " + task!!.name)
            }*/
            return task
        }

        fun runTask(mustHave: Boolean = true) {
            if (!isShut) {
                val task = getTask(mustHave)
                if (task == null || Thread.currentThread().isInterrupted()) {
                    return
                }
                try {
                    //println(name + " captured " + task.name)
                    task.run(Thread.currentThread() as MyThread)
                    println(Thread.currentThread().getName() + " released " + task.name)
                    //println(name + " completed " + task.name)
                } catch (e: InterruptedException) {
                    println(Thread.currentThread().getName() + " caught IE from " + task.name)
                    //println("thrown exeption by " + task.name)
                }
            }
        }

        override fun run() {
            while (!isShut) {
                Thread.interrupted()
                runTask()
            }
        }
    }
}