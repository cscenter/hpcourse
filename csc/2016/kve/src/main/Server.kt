package main

import communication.Protocol
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger


class Server(executorSize: Int = 800) {
    private var idCounter: AtomicInteger = AtomicInteger()
    val myTaskExecutor: TaskExecutor = TaskExecutor(executorSize)

    fun create() {
        val serverSocket = ServerSocket(47255)
        Thread { run(serverSocket) }.start()
        myTaskExecutor.create()
    }

    private fun run(serverSocket: ServerSocket) {

        while (true) {
            val socket = serverSocket.accept()

            val requestWrapper = Protocol.WrapperMessage.parseDelimitedFrom(socket.inputStream)
            val request = requestWrapper.request
            when {
                request.submit.isInitialized -> {
                    submitTask(request, socket)
                }

                request.subscribe.isInitialized -> {
                    myTaskExecutor.subscribeToResults(request.subscribe.taskId, request.requestId, socket)
                }

                request.list.isInitialized -> {
                    val listTasks = myTaskExecutor.listTasks()
                    sendListResponse(listTasks!!, request.requestId, socket)
                }
            }
        }
    }

    private fun submitTask(request: Protocol.ServerRequest, socket: Socket) {
        val task = request.submit.task
        if (task != null) {
            val taskId = idCounter.andIncrement
            val myTask = Task(task, taskId, request.clientId, this)
            sendSubmitResponse(request.requestId, socket.outputStream, taskId)
            myTaskExecutor.execute(myTask)
        }
    }

    private fun sendSubmitResponse(id: Long, outputStream: OutputStream, taskId: Int) {
        val response: Protocol.ServerResponse = Protocol.ServerResponse.newBuilder()
                .setRequestId(id)
                .setSubmitResponse(Protocol.SubmitTaskResponse.newBuilder()
                        .setSubmittedTaskId(taskId)
                        .setStatus(Protocol.Status.OK)).build()
        val responseWrapper = Protocol.WrapperMessage.newBuilder().setResponse(response).build()
        print("Response created: " + response.toString())
        try {
            responseWrapper.writeDelimitedTo(outputStream)
            outputStream.flush()
        } catch(e: Exception) {
            print("Error while sending response: " + e.message + e.cause)
        }
    }
}