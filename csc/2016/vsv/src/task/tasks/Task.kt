package task.tasks

import communication.CommunicationProtos


interface Task {
    val requestId: String
    fun register()
    fun execute(): CommunicationProtos.ServerResponse
    fun isReady(): Boolean
    fun getType(): TaskType
}


enum class TaskType {
    CALCULATE, SUBSCRIBE, LIST
}
