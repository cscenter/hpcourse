package communication

import taskExecution.Task
import java.net.Socket

fun sendSubscribeResponse(result: Long, requestId: Long, socket: Socket) {
    val serverResponse = Protocol.ServerResponse.newBuilder().apply {
        this.requestId = requestId
        this.subscribeResponse = (Protocol.SubscribeResponse.newBuilder().apply {
            value = result
            status = Protocol.Status.OK
        }.build())
    }

    Protocol.WrapperMessage.newBuilder().setResponse(serverResponse).build().writeDelimitedTo(socket.outputStream)
    socket.outputStream.flush()
}

fun sendListResponse(tasks: Collection<Task>, requestId: Long, socket: Socket) {
    val taskDescription = getTaskDescription(tasks)
    val response = Protocol.ServerResponse.newBuilder().apply {
        this.requestId = requestId
        listResponse = Protocol.ListTasksResponse.newBuilder()
                .setStatus(Protocol.Status.OK)
                .addAllTasks(taskDescription).build()
    }

    Protocol.WrapperMessage.newBuilder().setResponse(response).build().writeDelimitedTo(socket.outputStream)
    socket.outputStream.flush()
}

fun getTaskDescription(tasks: Collection<Task>): Collection<Protocol.ListTasksResponse.TaskDescription> {
    return tasks.map {
        Protocol.ListTasksResponse.TaskDescription.newBuilder().apply {
            clientId = it.clientId.toString()
            clientId = it.clientId.toString()
            if (it.result != null) {
                result = it.result as Long
            }
            taskId = it.id
            task = it.task
        }.build()
    }
}