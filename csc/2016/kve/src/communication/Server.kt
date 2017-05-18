package communication

import taskExecution.Task
import taskExecution.TaskExecutor
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger


class Server(executorSize: Int = 800, val port: Int = 47255) {
    private var idCounter: AtomicInteger = AtomicInteger()
    val myTaskExecutor: TaskExecutor = TaskExecutor(executorSize)

    fun start() {
        Thread { run() }.start()
        myTaskExecutor.create()
    }

    private fun run() {
        val serverSocket = ServerSocket(port)
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
                    sendListResponse(myTaskExecutor.listTasks, request.requestId, socket)
                }
            }
        }
    }

    private fun submitTask(request: Protocol.ServerRequest, socket: Socket) {
        val task = request.submit.task
        if (task != null) {
            val taskId = idCounter.andIncrement
            val myTask = Task(task, taskId, request.clientId)
            sendSubmitResponse(request.requestId, socket.outputStream, taskId)
            myTaskExecutor.execute(myTask)
        }
    }

    private fun sendSubmitResponse(id: Long, outputStream: OutputStream, taskId: Int) {
        val responseWrapper = Protocol.WrapperMessage.newBuilder().apply { 
            this.response = Protocol.ServerResponse.newBuilder().apply {
                requestId = id
                submitResponse = Protocol.SubmitTaskResponse.newBuilder().apply {
                    submittedTaskId = taskId
                    status = Protocol.Status.OK
                }.build()
            }.build()
        }.build()
        
        print("Response created: " + Protocol.ServerResponse.newBuilder()
                .setRequestId(id)
                .setSubmitResponse(Protocol.SubmitTaskResponse.newBuilder()
                        .setSubmittedTaskId(taskId)
                        .setStatus(Protocol.Status.OK)).build().toString())
        try {
            responseWrapper.writeDelimitedTo(outputStream)
            outputStream.flush()
        } catch(e: Exception) {
            print("Error while sending response: " + e.message + e.cause)
        }
    }
}