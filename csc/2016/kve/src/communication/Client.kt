package communication

import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class Client (clientId: Int = 1, val port: Int = 47255) {
    private val myClientId = clientId.toString()
    private var nyRequestIdCounter: AtomicLong = AtomicLong()
    private val myHost = "localhost"

    fun sendSubmitTask(a: Long = 1, b: Long = 1, m: Long = 1, n: Long = 4, p: Long = 1) {
        sendTask(createSubmitRequest(a, b, m, n, p))
    }

    fun sendSubmitTaskWithDependencies(a: Int = 1, b: Int = 1, n: Long = 4, p: Int = 1) {
        sendTask(createSubmitRequestWithDependencies(a, b, n, p))
    }

    fun sendSubscribeTask(id: Int) {
        sendTask(createSubscribeRequest(id))
    }

    fun sendListTask() {
        sendTask(createListRequest())
    }

    fun sendTask(request: Protocol.WrapperMessage) {
        val sock: Socket = Socket(myHost, port)
        request.writeDelimitedTo(sock.outputStream)
        sock.outputStream.flush()
        val serverResponse = Protocol.WrapperMessage.parseDelimitedFrom(sock.inputStream)
        print(serverResponse)
    }

    private fun createSubmitRequest(a: Long, b: Long, m: Long, n: Long, p: Long): Protocol.WrapperMessage {
        val builder: Protocol.Task.Builder? = Protocol.Task.newBuilder().apply {
            this.a = createParam(a)
            this.b = createParam(b)
            this.m = createParam(m)
            this.n = n
            this.p = createParam(p)
        }
        val submitTask: Protocol.SubmitTask = Protocol.SubmitTask.newBuilder().setTask(builder).build()
        val serverRequest = Protocol.ServerRequest.newBuilder().apply {
            clientId = myClientId
            requestId = nyRequestIdCounter.andIncrement
            submit = submitTask            
        }.build()

        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }

    private fun createSubmitRequestWithDependencies(a: Int, b: Int, n: Long, p: Int): Protocol.WrapperMessage {
        val builder: Protocol.Task.Builder? = Protocol.Task.newBuilder().apply {
            this.a = createDependency(a)
            this.b = createDependency(b)
            this.m = createParam(3)
            this.n = n
            this.p = createDependency(p)
        }
        val submitTask: Protocol.SubmitTask = Protocol.SubmitTask.newBuilder().setTask(builder).build()
        val serverRequest = Protocol.ServerRequest.newBuilder().apply {
            clientId = myClientId
            requestId = nyRequestIdCounter.andIncrement
            submit = submitTask
        }.build()

        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }

    private fun createParam(value: Long) = Protocol.Task.Param.newBuilder().setValue(value).build()
    
    private fun createDependency(dependencyId: Int) = Protocol.Task.Param.newBuilder().setDependentTaskId(dependencyId).build()

    private fun createSubscribeRequest(taskId: Int): Protocol.WrapperMessage {
        return Protocol.WrapperMessage.newBuilder().apply { 
         request = Protocol.ServerRequest.newBuilder().apply {
             subscribe = Protocol.Subscribe.newBuilder().setTaskId(taskId).build()
             clientId = myClientId
             requestId = nyRequestIdCounter.andIncrement
         }.build() 
        }.build()
    }

    private fun createListRequest(): Protocol.WrapperMessage {
        val listTasks = Protocol.ListTasks.newBuilder().build()
        val serverRequest = Protocol.ServerRequest.newBuilder().apply {
            list = listTasks
            clientId = myClientId
            requestId = nyRequestIdCounter.andIncrement
        }.build()
        return Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build()
    }
}
