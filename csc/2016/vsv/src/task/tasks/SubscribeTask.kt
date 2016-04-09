package task.tasks

import communication.CommunicationProtos


/**
 * @author Sergey Voytovich (voytovich.sergeey@gmail.com) on 09.04.16
 */

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
