package task.tasks

import communication.CommunicationProtos


interface Task {
    val requestId: String
    fun register()
    fun execute(): TaskResponse
    fun isReady(): Boolean
}

interface TaskResponse {
    val type: TaskType
    fun toServerResponse(): CommunicationProtos.ServerResponse
}

enum class TaskType {
    CALCULATE, SUBSCRIBE, LIST
}
