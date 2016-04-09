package task.tasks

import communication.CommunicationProtos


/**
 * @author Sergey Voytovich (voytovich.sergeey@gmail.com) on 09.04.16
 */

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
