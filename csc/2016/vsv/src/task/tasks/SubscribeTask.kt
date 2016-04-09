package task.tasks

import communication.CommunicationProtos

class SubscribeTask(request: CommunicationProtos.ServerRequest) : Task {
    override val requestId: String
        get() = throw UnsupportedOperationException()

    override fun register() {
        throw UnsupportedOperationException()
    }

    override fun execute(): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }

    override fun isReady(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun getType(): TaskType {
        throw UnsupportedOperationException()
    }

}
