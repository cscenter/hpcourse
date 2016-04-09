package task.tasks

import communication.CommunicationProtos

class SubscribeTask(clientId: String, requestId: Long, request: CommunicationProtos.Subscribe) : Task {
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
