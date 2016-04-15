package test

import communication.Protocol
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class Client {
    val CLIENT_ID = "1"
    var requestIdCounter: AtomicLong = AtomicLong()

    private val host = "localhost"
    private val port = 47255

    fun sendSubmitTask(a: Long = 1, b: Long = 1, m: Long = 1, n: Long = 4, p: Long = 1) {
        sendTask(createSubmitRequest(a, b, m, n, p))
    }

    fun sendSubscribeTask(id: Int) {
        sendTask(createSubscribeRequest(id))
    }

    fun sendListTask() {
        sendTask(createListRequest())
    }

    fun sendTask(request: Protocol.WrapperMessage) {
        val sock: Socket = Socket(host, port)
        request.writeDelimitedTo(sock.outputStream)
        sock.outputStream.flush()
        val serverResponse = Protocol.WrapperMessage.parseDelimitedFrom(sock.inputStream)
        print(serverResponse)
    }

    private fun createSubmitRequest(a: Long, b: Long, m: Long, n: Long, p: Long): Protocol.WrapperMessage {
        val builder: Protocol.Task.Builder? = Protocol.Task.newBuilder()
                .setA(setParam(a))
                .setB(setParam(b))
                .setM(setParam(m))
                .setN(n)
                .setP(setParam(p))
        val submitTask: Protocol.SubmitTask = Protocol.SubmitTask.newBuilder().setTask(builder).build()
        val serverRequest = Protocol.ServerRequest.newBuilder()
                .setClientId(CLIENT_ID)
                .setRequestId(requestIdCounter.andIncrement)
                .setSubmit(submitTask)
                .build()

        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }

    private fun setParam(value: Long) = Protocol.Task.Param.newBuilder().setValue(value)

    private fun createSubscribeRequest(taskId: Int): Protocol.WrapperMessage {
        val mySubscribe = Protocol.Subscribe.newBuilder().setTaskId(taskId).build()
        val serverRequest = Protocol.ServerRequest.newBuilder().apply {
            subscribe = mySubscribe
            clientId = CLIENT_ID
            requestId = requestIdCounter.andIncrement
        }.build()
        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }

    private fun createListRequest(): Protocol.WrapperMessage {
        val listTasks = Protocol.ListTasks.newBuilder().build()
        val serverRequest = Protocol.ServerRequest.newBuilder().apply {
            list = listTasks
            clientId = CLIENT_ID
            requestId = requestIdCounter.andIncrement
        }.build()
        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }
}
