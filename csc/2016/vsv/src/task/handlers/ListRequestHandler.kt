package task.handlers

import communication.CommunicationProtos

class ListRequestHandler(private val myReceivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest>,
                         private val myCompletedTasks: MutableMap<Int, Long>) {

    fun execute(request: CommunicationProtos.ListTasks): CommunicationProtos.ListTasksResponse {
        return listTask(myReceivedTasks, myCompletedTasks)
    }

    private fun listTask(receivedTasks: MutableMap<Int, CommunicationProtos.ServerRequest>,
                         completedTasks: MutableMap<Int, Long>): CommunicationProtos.ListTasksResponse {
        val tasksResponseBuilder = CommunicationProtos.ListTasksResponse.newBuilder();
        synchronized(receivedTasks, {
            for ((id, request) in receivedTasks) {
                val result = if (completedTasks.containsKey(id)) completedTasks.get(id) else null
                tasksResponseBuilder.addTasks(getTaskDescription(id, request, result))
            }
        })
        tasksResponseBuilder.setStatus(CommunicationProtos.Status.OK)
        return tasksResponseBuilder.build()
    }

    private fun getTaskDescription(id: Int,
                                   req: CommunicationProtos.ServerRequest,
                                   result: Long?): CommunicationProtos.ListTasksResponse.TaskDescription {
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
