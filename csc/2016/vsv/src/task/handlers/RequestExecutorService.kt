package task.handlers

import communication.CommunicationProtos
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
        var _response: CommunicationProtos.ServerResponse? = null

        if (request.hasSubmit()) {
            println("Submit request received")
            _response = handleSubmit()
        } else if (request.hasSubscribe()) {
            println("Subscribe request received")
            _response = handleSubscribe()
        } else if (request.hasList()) {
            println("List request received")
            _response = handleList()
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

    private fun handleSubmit(): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }

    private fun handleSubscribe(): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }

    private fun handleList(): CommunicationProtos.ServerResponse {
        throw throw UnsupportedOperationException()
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
