package handlers.requests

import communication.CommunicationProtos


object TaskResultToResponseBuilder {

    fun fromSubmitTask(requestId: Long, result: CommunicationProtos.SubmitTaskResponse): CommunicationProtos.ServerResponse {
        return CommunicationProtos.ServerResponse.newBuilder()
                .setRequestId(requestId)
                .setSubmitResponse(result)
                .build()
    }

    fun fromListTask(requestId: Long, result: CommunicationProtos.ListTasksResponse): CommunicationProtos.ServerResponse {
        return CommunicationProtos.ServerResponse.newBuilder()
                .setRequestId(requestId)
                .setListResponse(result)
                .build()
    }

    fun fromSubscribeTask(requestId: Long, result: CommunicationProtos.SubscribeResponse): CommunicationProtos.ServerResponse {
        return CommunicationProtos.ServerResponse.newBuilder()
                .setRequestId(requestId)
                .setSubscribeResponse(result)
                .build()
    }
}
