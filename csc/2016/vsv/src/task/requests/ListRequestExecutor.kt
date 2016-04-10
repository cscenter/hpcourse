package task.requests

import communication.CommunicationProtos

class ListRequestExecutor(val myTasks: Map<CommunicationProtos.ServerRequest, Long?>) : RequestExecutor {

    override fun execute(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        return TaskResultToResponseBuilder.fromListTask(request.requestId, listTask(myTasks))
    }

    fun listTask(tasks: Map<CommunicationProtos.ServerRequest, Long?>): CommunicationProtos.ListTasksResponse {
        val tasksResponseBuilder = CommunicationProtos.ListTasksResponse.newBuilder();
        synchronized(tasks, {
            for ((request, result) in tasks) {
                tasksResponseBuilder.addTasks(getTaskDesctiption(request, result))
            }
        })
        tasksResponseBuilder.setStatus(CommunicationProtos.Status.OK)
        return tasksResponseBuilder.build()
    }

    private fun getTaskDesctiption(req: CommunicationProtos.ServerRequest, result: Long?): CommunicationProtos.ListTasksResponse.TaskDescription {
        val descriprion = CommunicationProtos.ListTasksResponse.TaskDescription.newBuilder()
                .setClientId(req.clientId)
                .setTaskId(req.requestId.toInt())
                .setTask(req.submit.task)
        if (result != null) {
            descriprion.result = result
        }
        return descriprion.build()

    }
}
