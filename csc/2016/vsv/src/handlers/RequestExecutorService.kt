package handlers

import communication.CommunicationProtos
import handlers.requests.TaskResultToResponseBuilder
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.*

class RequestExecutorService {

    private val receivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest> = LinkedHashMap()
    private val completedTasks: MutableMap<Int, Long> = LinkedHashMap()
    private val runningTasksLock: MutableMap<Int, Object> = LinkedHashMap()

    private var lastId: Int = 0

    fun execute(clientSocket: Socket) {
        invokeInNewThread {

            val request = getRequest(clientSocket.inputStream)
            clientSocket.inputStream.close()

            if (request.isInitialized) {
                println("Error on parse request")
                Thread.currentThread().interrupt()
            }

            val response = handleRequest(request)
            clientSocket.outputStream.use {
                sendResponse(it, response)
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

    private fun buildErrorResponse(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        return CommunicationProtos.ServerResponse.newBuilder()
                .setRequestId(request.requestId)
                .build()
    }

    private fun handleSubmit(request: CommunicationProtos.ServerRequest): CommunicationProtos.SubmitTaskResponse {
        val id = getNextId()

        invokeInNewThread {
            //register task, create lock
            val monitor = Object()
            synchronized(receivedTasks) {
                receivedTasks[id] = request
                runningTasksLock[id] = Object()
            }

            val handler = SubmitRequestHandler(id, { id -> waitAndGet(id) })
            val taskResult = handler.handle(request.submit)

            if (taskResult != null) {
                completedTasks.put(id, taskResult)
            }

            synchronized(monitor, {
                println("Notify all waiting for $id")
                monitor.notifyAll()
            })
            runningTasksLock.remove(id)
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
        val handler = ListRequestHandler(receivedTasks, completedTasks)
        return handler.handle(request.list)
    }


    private fun invokeInNewThread(action: () -> Unit) {
        val task = Thread(Runnable { action() })
        task.start()
    }

    private fun getRequest(ism: InputStream) = CommunicationProtos.ServerRequest.parseFrom(ism)

    private fun sendResponse(osm: OutputStream, response: CommunicationProtos.ServerResponse) = osm.write(response.toByteArray())

    private fun getNextId(): Int {
        synchronized(lastId, {
            return ++lastId
        })
    }

    private fun waitAndGet(taskId: Int): Long? {
        val monitor: Object = runningTasksLock.get(taskId) ?: return null
        synchronized(monitor, {
            if (!completedTasks.containsKey(taskId)) {
                monitor.wait()
            }
        })
        assert(completedTasks.containsKey(taskId), { "Result didn't apeared but subsriber was awake" })
        val result: Long = completedTasks[taskId] ?: return null
        return result
    }
}
