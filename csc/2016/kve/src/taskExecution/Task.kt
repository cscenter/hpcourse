package taskExecution

import collections.SynchronizedMap
import communication.Protocol
import communication.sendSubscribeResponse
import java.net.Socket
import java.util.*

class Task(val task: Protocol.Task, val id: Int, val clientId: String){
    var result: Long? = null
    var subscribers = SynchronizedMap<Long, Socket>();
    
    fun runFunction(a: Long, b: Long, p: Long, m: Long): Long {
        return taskFunction(a, b, p, m, task.n)
    }


    fun subscribe(requestId: Long, socket: Socket) {
        subscribers.put(requestId, socket)
        if (result != null) {
            sendResult()
        }
    }

    fun sendResult() {
        for (requestId in subscribers.keys) {
            val socket = subscribers[requestId]
            if (result != null) {
                sendSubscribeResponse(result as Long, requestId, socket!!)
            }
        }
    }
    
    fun getDependenciesIds(): HashMap<String, Int> {
        val list = HashMap<String, Int>()
        if (task.a.hasDependentTaskId()) {
            list["a"]  = task.a.dependentTaskId
        }
        if (task.b.hasDependentTaskId()) {
            list["b"] = task.b.dependentTaskId
        }
        if (task.m.hasDependentTaskId()) {
            list["m"] = task.m.dependentTaskId
        }
        if (task.p.hasDependentTaskId()) {
            list["p"] = task.p.dependentTaskId
        }
        return list
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Task

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int{
        return id
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