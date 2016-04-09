package task.tasks

import communication.CommunicationProtos


class ListTask(clientId: String, requestId: Long, request: CommunicationProtos.ListTasks) : Task {
    override val requestId: String
        get() = throw UnsupportedOperationException()

    override fun register() {
        throw UnsupportedOperationException()
    }

    override fun execute(): TaskResponse {
        throw UnsupportedOperationException()
    }

    override fun isReady(): Boolean {
        throw UnsupportedOperationException()
    }
}
