package task.handlers.requests

import communication.CommunicationProtos

class ListRequestExecutor(val myReceivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest>,
                          val myCompletedTasks: MutableMap<Int, Long>) : RequestExecutor {

    override fun execute(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        return TaskResultToResponseBuilder.fromListTask(request.requestId, listTask(myReceivedTasks, myCompletedTasks))
    }

    fun listTask(receivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest>,
                 completedTasks: MutableMap<Int, Long>): CommunicationProtos.ListTasksResponse {
        val tasksResponseBuilder = CommunicationProtos.ListTasksResponse.newBuilder();
        synchronized(receivedTasks, {
            for ((id, request) in receivedTasks) {
                val result = if (completedTasks.containsKey(id)) completedTasks.get(id) else null
                tasksResponseBuilder.addTasks(getTaskDesctiption(id, request, result))
            }
        })
        tasksResponseBuilder.setStatus(CommunicationProtos.Status.OK)
        return tasksResponseBuilder.build()
    }

    private fun getTaskDesctiption(id: Int, req: CommunicationProtos.ServerRequest, result: Long?): CommunicationProtos.ListTasksResponse.TaskDescription {
        val description = CommunicationProtos.ListTasksResponse.TaskDescription.newBuilder()
                .setClientId(req.clientId)
                .setTaskId(id)
                .setTask(req.submit.task)
        if (result != null) {
            description.result = result
        }
        return description.build()

    }
}
