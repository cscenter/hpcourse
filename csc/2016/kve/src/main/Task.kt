package main

import communication.Protocol
import java.net.Socket

class Task {
    var result: Long? = null
    val myTask: Protocol.Task
    var subscribers = SynchronizedMap<Long, Socket>();
    val myServer: Server;
    val myId: Int
    val myClientId: String

    constructor(task: Protocol.Task, id: Int, clientId: String, server: Server) {
        myTask = task
        myId = id
        myClientId = clientId
        myServer = server
    }

    fun runFunction(): Long {
        return taskFunction(myTask.a.value, myTask.b.value, myTask.p.value, myTask.m.value, myTask.n)
    }

    fun getId(): Int {
        return myId
    }


    fun subscribe(requestId: Long, socket: Socket) {
        subscribers.put(requestId, socket)
        if (result != null) {
            sendResult()
        }
    }

    fun sendResult() {
        for (requestId in subscribers.keys) {
            val socket = subscribers.get(requestId)
            if (result != null) {
                sendSubscribeResponse(result as Long, requestId, socket)
            }
        }
    }
}

fun taskFunction(a: Long, b: Long, p: Long, m: Long, n: Long): Long {
    var myN = n
    var myB = b
    var myA = a
    while (myN-- > 0) {
        myB = (myA * p + myB) % m;
        myA = myB
    }
    return myA;
}