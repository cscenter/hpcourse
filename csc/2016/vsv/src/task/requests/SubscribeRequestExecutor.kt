package task.requests

import communication.CommunicationProtos


class SubscribeRequestExecutor() : RequestExecutor {
    override fun execute(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }

}
