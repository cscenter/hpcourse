package task.handlers

import communication.CommunicationProtos
import task.handlers.requests.CalculateRequestExecutor
import task.handlers.requests.ListRequestExecutor
import task.handlers.requests.RequestExecutor
import task.handlers.requests.SubscribeRequestExecutor
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.*

class RequestExecutorService {

    private val receivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest> = LinkedHashMap()
    private val completedTasks: MutableMap<Int, Long> = LinkedHashMap()

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
        var _executor: RequestExecutor? = null

        if (request.hasSubmit()) {
            _executor = CalculateRequestExecutor()
        } else if (request.hasSubscribe()) {
            _executor = SubscribeRequestExecutor()
        } else if (request.hasList()) {
            _executor = ListRequestExecutor(receivedTasks, completedTasks)
        }

        //TODO: handle error properly, send response
        val executor: RequestExecutor = _executor ?: return buildErrorResponse(request)
        val response = executor.execute(request)

        println("${request.requestId} has finished")
        return response
    }

    private fun buildErrorResponse(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }

    private fun buildSubmitError(taskId: Int): CommunicationProtos.SubmitTaskResponse {
        return CommunicationProtos.SubmitTaskResponse.newBuilder()
                .setSubmittedTaskId(taskId)
                .setStatus(CommunicationProtos.Status.ERROR)
                .build()
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
}
