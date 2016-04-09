package task

import communication.CommunicationProtos
import task.tasks.CalculateTask
import task.tasks.ListTask
import task.tasks.SubscribeTask
import task.tasks.Task
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
        var _task: Task? = null

        if (request.hasSubmit()) {
            _task = CalculateTask(request)
        } else if (request.hasSubscribe()) {
            _task = SubscribeTask(request)
        } else if (request.hasList()) {
            _task = ListTask(request)
        }
        //TODO: do not trow an exception
        val task = _task ?: throw RuntimeException("Meaningless task received: task type is not set")
        task.register()
        val response = task.execute()
        println("${task.requestId} has finished")
        return response

    }

    private fun invokeInNewThread(action: () -> Unit) {
        val task = Thread(Runnable { action() })
        task.start()
    }

    private fun getRequest(ism: InputStream) = CommunicationProtos.ServerRequest.parseFrom(ism)

    private fun sendResponse(osm: OutputStream, response: CommunicationProtos.ServerResponse) = osm.write(response.toByteArray())
}
