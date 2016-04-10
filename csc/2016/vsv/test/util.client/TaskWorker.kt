package util.client

import communication.CommunicationProtos
import java.io.InputStream
import java.net.Socket


/**
 * @author Sergey Voytovich (voytovich.sergeey@gmail.com) on 10.04.16
 */
class TaskWorker(val port: Int, val clientId: String) {

    private val localhost = "localhost"
    private var requestId: Long = 0

    fun list(): CommunicationProtos.ListTasksResponse {
        val request: CommunicationProtos.ServerRequest = getRequestBuilder()
                .setList(CommunicationProtos.ListTasks.newBuilder().build())
                .build()

        val response = writeRequestAndGetResponse(request)
        return response.listResponse
    }

    fun submit(a: Parameter, b: Parameter, p: Parameter, m: Parameter, n: Long): CommunicationProtos.SubmitTaskResponse {
        val taskRequest = CommunicationProtos.Task.newBuilder()
                .setA(toParam(a))
                .setB(toParam(b))
                .setP(toParam(p))
                .setM(toParam(m))
                .setN(n)
                .build()
        val request = CommunicationProtos.ServerRequest.newBuilder()
                .setSubmit(CommunicationProtos.SubmitTask.newBuilder().setTask(taskRequest))
                .build()

        val response = writeRequestAndGetResponse(request)
        return response.submitResponse
    }

    fun subscribe(taskId: Int): CommunicationProtos.SubscribeResponse {
        val subscribeRequest: CommunicationProtos.Subscribe = CommunicationProtos.Subscribe.newBuilder()
                .setTaskId(taskId)
                .build()
        val request = CommunicationProtos.ServerRequest.newBuilder()
                .setSubscribe(subscribeRequest)
                .build()
        val response = writeRequestAndGetResponse(request)
        return response.subscribeResponse
    }

    private fun writeRequestAndGetResponse(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        //TODO: close then
        val socket = Socket(localhost, port);
        request.writeTo(socket.outputStream);
        val response = getResponse(socket.inputStream)
        return response
    }

    fun toParam(p: Parameter): CommunicationProtos.Task.Param {
        if (p.isDependentTaskId) {
            return CommunicationProtos.Task.Param.newBuilder()
                    .setDependentTaskId(p.value.toInt())
                    .build()
        }
        return CommunicationProtos.Task.Param.newBuilder()
                .setValue(p.value)
                .build()
    }

    private fun getResponse(ism: InputStream): CommunicationProtos.ServerResponse {
        val size = ism.read()
        val data = ByteArray(size)
        ism.read(data)
        return CommunicationProtos.ServerResponse.parseFrom(data)
    }


    private fun getRequestBuilder(): CommunicationProtos.ServerRequest.Builder {
        return CommunicationProtos.ServerRequest.newBuilder()
                .setClientId(clientId)
                .setRequestId(getNextId())
    }

    private fun getNextId(): Long {
        return ++requestId
    }
}

data class Parameter(val value: Long, val isDependentTaskId: Boolean)
