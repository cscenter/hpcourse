package task

import communication.CommunicationProtos
import task.requests.CalculateRequestExecutor
import task.requests.ListRequestExecutor
import task.requests.RequestExecutor
import task.requests.SubscribeRequestExecutor
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.*

class RequestExecutorService {

    private val tasks: MutableMap<CommunicationProtos.ServerRequest, Long?> = HashMap()

    fun execute(clientSocket: Socket) {
        invokeInNewThread {

            val request = getRequest(clientSocket.inputStream)
            clientSocket.inputStream.close()

            if (request.isInitialized) {
                println("Error on parse request")
                Thread.currentThread().interrupt()
            }

            val response = handle(request)
            clientSocket.outputStream.use {
                sendResponse(it, response)
            }
        }
    }

    private fun handle(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        var _executor: RequestExecutor? = null

        if (request.hasSubmit()) {
            _executor = CalculateRequestExecutor()
        } else if (request.hasSubscribe()) {
            _executor = SubscribeRequestExecutor()
        } else if (request.hasList()) {
            _executor = ListRequestExecutor(tasks)
        }

        //TODO: handle error properly, send response
        val executor: RequestExecutor = _executor ?: throw RuntimeException("something is wrong")
        val response = executor.execute(request)

        println("${request.requestId} has finished")
        return response
    }

    private fun invokeInNewThread(action: () -> Unit) {
        val task = Thread(Runnable { action() })
        task.start()
    }

    private fun getRequest(ism: InputStream) = CommunicationProtos.ServerRequest.parseFrom(ism)

    private fun sendResponse(osm: OutputStream, response: CommunicationProtos.ServerResponse) = osm.write(response.toByteArray())
}
