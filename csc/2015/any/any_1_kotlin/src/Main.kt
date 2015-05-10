import ru.bronti.hpcource.hw1.MyFuture
import java.util.Random
import ru.bronti.hpcource.hw1.MyThreadPool
import java.util.ArrayList
import java.util.LinkedList
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException

val rand = Random()

val HELP =
"""
help
    there is some recursion

add N
    to add N sec task

cancel ID
    to cancel task by ID

status ID
    to get status

exit
    to wait wait for all tasks and exit

force exit
    to exit immediately
"""


fun main(args: Array<String>) {

    var size = 8
    if (args.size() < 1) {
        println("size wasn't set. default size = 8 used.")
    }
    else {
        size = args[0].toInt()
    }
    val tp = MyThreadPool(size)
    var futures = ArrayList<MyFuture<*>>()

    println("enter commands here. type 'help' if you need it.")

    var running = true
    while (running) {
        print("> ")
        val r = readLine()!!.trim().split(" ")
        if (r.size() == 0) {
            continue
        }
        try {
            when (r[0]) {
                "help" -> println(HELP)
                "add" -> {
                    val N: Long = r[1].toLong() * 1000
                    val future = tp.submit(object : Callable<Unit> {
                        override fun call() {
                            Thread.sleep(N)
                        }
                    })
                    val ID = futures.size()
                    futures.add(future)
                    println("task added. ID = " + ID)
                }
                "cancel" -> {
                    val ID: Int = r[1].toInt()
                    if (ID >= futures.size() || ID < 0) {
                        println("there is no such task")
                    }
                    else {
                        futures[ID].cancel(true)
                    }
                }
                "status" -> {
                    val ID: Int = r[1].toInt()
                    if (ID >= futures.size() || ID < 0) {
                        println("there is no such task")
                    } else {
                        val isDone = futures[ID].isDone()
                        val isCancelled = futures[ID].isCancelled()
                        when {
                            !isDone && !isCancelled -> println("waiting/running")
                            isDone && !isCancelled -> println("done")
                            isCancelled -> println("cancelled")
                        }
                    }
                }
                "exit" -> {
                    tp.shutdown()
                    running = false
                }
                "force" -> {
                    tp.shutdownNow()
                    running = false
                }
            }
        }
        catch (e: NumberFormatException) {
            println("incorrect input")
        }
    }

//    val tp = MyThreadPool(20)
//    var futures = LinkedList<MyFuture<*>>()
//
//    for (i in 1..100) {
//        val future = (tp.submit(object : Callable<String> {
//            override fun call(): String {
//                println("task " + i + " started")
//                Thread.sleep(1000L + rand.nextInt(4000))
//                //println("task " + i + " complete")
//                return " i = " + i
//            }
//        }, "task " + i))
//
//        futures.add(future)
//        if (i > 80) {
//            future!!.cancel(false)
//        }
//    }
//
//    for (f in futures) {
//        Thread({
//            try {
//                val res = f.get()
//                println("from get: " + f.name + " was completed with answer: " + res)
//            } catch (e: Exception) {
//                println("from get: " + f.name + " was intrrupted by " + e.javaClass)
//            }
//        }).start()
//    }
//
//    for (i in 40..50) {
//        futures.poll().cancel(false)
//    }
//    for (i in 50..60) {
//        futures.poll().cancel(true)
//    }
//    Thread.sleep(3000L + rand.nextInt(2000))
}