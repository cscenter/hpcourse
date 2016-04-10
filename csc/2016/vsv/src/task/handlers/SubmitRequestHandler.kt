package task.handlers

import communication.CommunicationProtos
import task.handlers.requests.RequestExecutor


class SubmitRequestHandler() : RequestExecutor {
    override fun execute(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        throw UnsupportedOperationException()
    }
}
