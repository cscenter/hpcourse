package handlers

import communication.CommunicationProtos


class SubmitRequestHandler(private val taskId: Int, private val waitDependentTask: (taskId: Int) -> Long?) {
    fun handle(request: CommunicationProtos.SubmitTask): Long? {
        val task: Task = getTask(request) ?: return null
        return calculate(task)
    }

    private fun calculate(task: Task): Long {
        var a = task.a
        var b = task.b
        var p = task.p
        var m = task.m
        var n = task.n

        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a
    }

    private fun getTask(request: CommunicationProtos.SubmitTask): Task? {
        try {
            val a: Long = waitAndGet(request.task.a)
            val b: Long = waitAndGet(request.task.b)
            val p: Long = waitAndGet(request.task.p)
            val m: Long = waitAndGet(request.task.m)
            val n: Long = request.task.n
            return Task(a, b, p, m, n)
        } catch(e: RuntimeException) {
            println("Failed wait dependent paramenters")
            return null
        }
    }

    private fun waitAndGet(param: CommunicationProtos.Task.Param): Long {
        if (param.hasValue()) {
            return param.value
        }
        val result: Long = waitDependentTask(param.dependentTaskId) ?: throw RuntimeException("Dependent task waiting failed")
        return result
    }
}

data class Task(val a: Long, val b: Long, val p: Long, val m: Long, val n: Long)
