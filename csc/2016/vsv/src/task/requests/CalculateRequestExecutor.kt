package task.requests

import communication.CommunicationProtos


class CalculateRequestExecutor() : RequestExecutor {
    override fun execute(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }
}
