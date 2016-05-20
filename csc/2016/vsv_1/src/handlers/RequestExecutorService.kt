package handlers

import communication.CommunicationProtos
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RequestExecutorService {

    private val receivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest> = LinkedHashMap()
    private val completedTasks = AtomicReference<MutableMap<Int, Long>>(LinkedHashMap())
    private val runningTasksLock: MutableMap<Int, Object> = LinkedHashMap()

    private var lastId = AtomicInteger(0)

    fun execute(clientSocket: Socket) {
        invokeInNewThread {

            val request = getRequest(clientSocket.inputStream)

            val response = if (request != null) handleRequest(request) else buildErrorResponse(null)
            if (!clientSocket.isClosed) {
                sendResponse(clientSocket.outputStream, response)
            }
        }
    }

    private fun handleRequest(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        var _response: CommunicationProtos.ServerResponse? = null

        if (request.hasSubmit()) {
            println("Submit request received")
            _response = TaskResultToResponseBuilder.fromSubmitTask(request.requestId, handleSubmit(request))
        } else if (request.hasSubscribe()) {
            println("Subscribe request received")
            _response = TaskResultToResponseBuilder.fromSubscribeTask(request.requestId, handleSubscribe(request))
        } else if (request.hasList()) {
            println("List request received")
            _response = TaskResultToResponseBuilder.fromListTask(request.requestId, handleList(request))
        }

        val response: CommunicationProtos.ServerResponse = _response ?: return buildErrorResponse(request)

        println("Complete ${request.requestId}  hadnling")
        return response
    }

    private fun buildErrorResponse(request: CommunicationProtos.ServerRequest?): CommunicationProtos.ServerResponse {
        val response = CommunicationProtos.ServerResponse.newBuilder()
        if (request != null) {
            response.requestId = request.requestId
        }
        return response.build()
    }

    private fun handleSubmit(request: CommunicationProtos.ServerRequest): CommunicationProtos.SubmitTaskResponse {
        val id = getNextId()

        invokeInNewThread {
            println("Start calculation $id")
            //register task, create lock
            val monitor = Object()
            synchronized(receivedTasks) {
                println("Submit task blocked received task")
                receivedTasks[id] = request
                runningTasksLock[id] = monitor
            }
            println("Submit task released received task")


            val handler = SubmitRequestHandler(id, { id -> waitAndGet(id) })
            val taskResult = handler.handle(request.submit)
            println("Got result for $id - $taskResult")
            if (taskResult != null) {
                println("Write result in completed task list")
                completedTasks.get().put(id, taskResult)
            }

            runningTasksLock.remove(id)
            synchronized(monitor, {
                println("Submit task blocked monitor for $id task")
                println("Notify all waiting for $id")
                monitor.notifyAll()
            })
            println("Submit task released monitor for $id task")

            println("$id calculation has finished")
        }

        return CommunicationProtos.SubmitTaskResponse.newBuilder()
                .setSubmittedTaskId(id)
                .setStatus(CommunicationProtos.Status.OK)
                .build()
    }

    private fun handleSubscribe(_request: CommunicationProtos.ServerRequest): CommunicationProtos.SubscribeResponse {
        val request = _request.subscribe
        println("Get the monitor lock")
        val result: Long = waitAndGet(request.taskId) ?: return buildSubscribeRequestError()
        return CommunicationProtos.SubscribeResponse.newBuilder()
                .setValue(result)
                .setStatus(CommunicationProtos.Status.OK)
                .build()
    }

    private fun buildSubscribeRequestError(): CommunicationProtos.SubscribeResponse {
        return CommunicationProtos.SubscribeResponse.newBuilder()
                .setStatus(CommunicationProtos.Status.ERROR)
                .build()
    }

    private fun handleList(request: CommunicationProtos.ServerRequest): CommunicationProtos.ListTasksResponse {
        //strange thing happened with handlers idea
        val handler = ListRequestHandler(receivedTasks, completedTasks.get())
        return handler.handle(request.list)
    }


    private fun invokeInNewThread(action: () -> Unit) {
        val task = Thread(Runnable { action() })
        task.start()
    }

    private fun getRequest(ism: InputStream): CommunicationProtos.ServerRequest? {
        val wrappedMessage = CommunicationProtos.WrapperMessage.parseDelimitedFrom(ism)
        if (!wrappedMessage.hasRequest()) {
            return null
        }
        return wrappedMessage.request
    }

    private fun sendResponse(osm: OutputStream, response: CommunicationProtos.ServerResponse) {
        val wm = CommunicationProtos.WrapperMessage.newBuilder()
                .setResponse(response)
                .build()
        wm.writeDelimitedTo(osm)
    }

    private fun getNextId(): Int {
        return lastId.incrementAndGet()
    }

    private fun waitAndGet(taskId: Int): Long? {
        if (completedTasks.get().containsKey(taskId)) {
            return completedTasks.get()[taskId]
        }
        val monitor: Object = runningTasksLock[taskId] ?: return null
        synchronized(monitor, {
            println("Subscribe locked on $taskId monitor")
            println("Subscribe starting to wait for $taskId")
            monitor.wait()
            println("Subscribe awake to get  $taskId result")
        })
        assert(completedTasks.get().containsKey(taskId), { "Result didn't apeared but subsriber was awake" })
        val result: Long = completedTasks.get()[taskId] ?: return null
        return result
    }
}
