package ru.bronti.hpcource.hw1

import java.lang
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

public class MyFuture<V> (private val task: Callable<V>, public val name: String): Future<V> {

    private val resultMonitor = Object()

    volatile var isRun = false
    volatile var isDone = false
    val isCancelled = AtomicBoolean(false)
                                     //todo: status + cas !!!!!!!!!!

    volatile var worker: Thread? = null
    volatile var result: V = null
    volatile var exeption: Exception? = null

    override public fun isDone(): Boolean = isDone
    override public fun isCancelled(): Boolean = isCancelled.get()

    override public fun get(timeout: Long, unit: TimeUnit): V {
        var timedOut: Boolean = false
        synchronized(resultMonitor) {
            val expirationTime = System.currentTimeMillis() + unit.toMillis(timeout);
            while (!isDone && !isCancelled()) {
                resultMonitor.wait(expirationTime - System.currentTimeMillis());
                if (System.currentTimeMillis() >= expirationTime) {
                    timedOut = true
                    break
                }
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
    }

    override public fun get(): V {
        synchronized(resultMonitor) {
            while (!isDone && !isCancelled()) {
                resultMonitor.wait()
            }
            if (isCancelled()) {
                throw CancellationException()
            }
            if (exeption != null) {
                throw ExecutionException(exeption)
            }
            return result
        }
    }

    override public fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        var wasDone: Boolean? = null
        synchronized(isCancelled) {
            synchronized(resultMonitor) {
                isCancelled.set(true)
                resultMonitor.notifyAll()
            }
            wasDone = isDone && !isRun
            if (mayInterruptIfRunning && isRun && !isDone) {
                worker!!.interrupt()
                //println(name + " interrupted while running")
            }
        }
//        if (!wasDone!! && !isRun) {
//            println(name + " interrupted while waiting")
//        }
//        if (!(!wasDone!! && !(isRun && !mayInterruptIfRunning))) {
//            println(name + " cancellation failed")
//        }
        return !wasDone!! && !(isRun && !mayInterruptIfRunning)
    }

    throws(javaClass<InterruptedException>())
    fun run(thread: Thread) {
        worker = thread
        synchronized(isCancelled) {
            if (!isCancelled()) {
                isRun = true
                //println(name + " running")
            }
        }
        var temp: V = null
        if (!isCancelled()) {
            try {
                temp = task.call()
            }
            catch (e: InterruptedException) {
                throw e
            }
            catch (e: Exception) {
                exeption = e
            }
        }
        else {
            //println("skipped execution by " + name)
        }
        synchronized(isCancelled) {
            synchronized(resultMonitor) {
                if (!isCancelled()) {
                    result = temp
                }
                isDone = true
                resultMonitor.notifyAll()
            }
            //println(name + " done")
            if (!isCancelled()) {
                isRun = false
            }
        }
    }

    fun getTaskAndCancel(): Callable<V> {
        cancel(true)
        return task
    }
}