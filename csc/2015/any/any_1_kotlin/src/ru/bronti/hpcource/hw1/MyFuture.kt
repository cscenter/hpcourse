package ru.bronti.hpcource.hw1

import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.lang.ThreadGroup
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

public class MyFuture<V> (private val task: Callable<V>,
                          val name: String,
                          private val threadPool: MyThreadPool): Future<V> {

    private val resultMonitor = Object()

    val WAITING = 0
    val RUNNING = 1
    val DONE    = 2
    val CANCELLED = 4

    val status = AtomicInteger(WAITING)

    volatile var workerThread: MyThread? = null
    volatile var result: V = null
    volatile var exeption: Exception? = null

    override public fun isDone(): Boolean = status.get() % CANCELLED >= DONE
    override public fun isCancelled(): Boolean = status.get() >= CANCELLED

    private fun doWaitOnce(expirationTime: Long?): Boolean {
        if (Thread.currentThread() is MyThread &&
                !threadPool.isShut &&
                !threadPool.queue.isEmpty() &&
                !isCancelled()) {
            //println(Thread.currentThread().getName() + " is waiting for " + name)
            val wasInterrupted = Thread.interrupted()
            (Thread.currentThread() as MyThread).task.runTask(false)
            if (wasInterrupted) {
                Thread.currentThread().interrupt()
            }
        }
        else {
            synchronized(resultMonitor) {
                if (!isCancelled()) {
                    if (expirationTime != null) {
                        resultMonitor.wait(expirationTime - System.currentTimeMillis())
                    } else {
                        resultMonitor.wait()
                    }
                }
            }
        }
        if (expirationTime != null && System.currentTimeMillis() >= expirationTime) {
            return true
        }
        return false
    }

    private fun doGet(expirationTime: Long?): V {
        var timedOut = false
        while (!isDone() && !isCancelled() && !timedOut) {
            timedOut = doWaitOnce(expirationTime)
        }
        if (isCancelled()) {
            throw CancellationException()
        }
        if (!timedOut) {
            if (exeption != null) {
                throw ExecutionException(exeption)
            }
            return result
        }
        else {
            throw TimeoutException()
        }
    }

    override public fun get(timeout: Long, unit: TimeUnit): V {
        val expirationTime = System.currentTimeMillis() + unit.toMillis(timeout);
        return doGet(expirationTime)
    }

    override public fun get(): V {
        return doGet(null)
    }

    override public fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        var result: Boolean = false
        synchronized(resultMonitor) {
            status.set(status.get() % CANCELLED + CANCELLED)
            resultMonitor.notifyAll()
        }
        if (mayInterruptIfRunning) {
            if (status.get() % CANCELLED == RUNNING) {
                workerThread!!.interrupt()
                result = true
            }
            //println(name + " interrupted while running")
        }
        result = result or (status.get() % CANCELLED == WAITING)
        status.set(CANCELLED + DONE + status.get() % DONE)
        return result
    }

    throws(javaClass<InterruptedException>())
    fun run(thread: MyThread) {
        workerThread = thread
        var temp: V = null
        if (status.compareAndSet(WAITING, RUNNING)) {
            //println(name + " running")
            try {
                temp = task.call()
            } catch (e: InterruptedException) {
                synchronized(resultMonitor) {
                    if (status.compareAndSet(RUNNING, RUNNING + DONE)) {
                        result = temp
                        resultMonitor.notifyAll()
                        throw e
                    }
                }
            } catch (e: Exception) {
                exeption = e
            }
            if (!isCancelled()) {
                synchronized(resultMonitor) {
                    if (status.compareAndSet(RUNNING, RUNNING + DONE)) {
                        result = temp
                        resultMonitor.notifyAll()
                    }
                }
            }
        }
        else {
            //println("skipped execution by " + name)
        }
    }

    fun getTaskAndCancel(): Callable<V> {
        cancel(true)
        return task
    }
}