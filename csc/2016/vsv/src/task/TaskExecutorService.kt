package task

import communication.CommunicationProtos
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class TaskExecutorService {


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
        throw UnsupportedOperationException()
    }

    private fun invokeInNewThread(action: () -> Unit) {
        val task = Thread(Runnable { action() })
        task.start()
    }

    private fun getRequest(ism: InputStream) = CommunicationProtos.ServerRequest.parseFrom(ism)

    private fun sendResponse(osm: OutputStream, response: CommunicationProtos.ServerResponse) = osm.write(response.toByteArray())
}
