package util.client

import communication.CommunicationProtos
import java.io.InputStream
import java.net.Socket


/**
 * @author Sergey Voytovich (voytovich.sergeey@gmail.com) on 10.04.16
 */
class TaskWorker(private val port: Int, private val clientId: String) {

    private val localhost = "localhost"
    private var requestId: Long = 0

    fun list(): CommunicationProtos.ListTasksResponse {
        val request: CommunicationProtos.ServerRequest = getRequestBuilder()
                .setList(CommunicationProtos.ListTasks.newBuilder().build())
                .build()

        val response = writeRequestAndGetResponse(request)
        return response.listResponse
    }

    fun submit(a: CommunicationProtos.Task.Param,
               b: CommunicationProtos.Task.Param,
               p: CommunicationProtos.Task.Param,
               m: CommunicationProtos.Task.Param,
               n: Long): CommunicationProtos.SubmitTaskResponse {
        val taskRequest = CommunicationProtos.Task.newBuilder()
                .setA(toParam(a))
                .setB(toParam(b))
                .setP(toParam(p))
                .setM(toParam(m))
                .setN(n)
                .build()
        val request = getRequestBuilder()
                .setSubmit(CommunicationProtos.SubmitTask.newBuilder().setTask(taskRequest))
                .build()

        val response = writeRequestAndGetResponse(request)
        return response.submitResponse
    }

    fun subscribe(taskId: Int): CommunicationProtos.SubscribeResponse {
        val subscribeRequest: CommunicationProtos.Subscribe = CommunicationProtos.Subscribe.newBuilder()
                .setTaskId(taskId)
                .build()
        val request = getRequestBuilder()
                .setSubscribe(subscribeRequest)
                .build()
        val response = writeRequestAndGetResponse(request)
        return response.subscribeResponse
    }

    private fun writeRequestAndGetResponse(request: CommunicationProtos.ServerRequest): CommunicationProtos.ServerResponse {
        //TODO: close then
        Socket(localhost, port).use {
            it.outputStream.write(request.serializedSize)
            request.writeTo(it.outputStream);
            val response = getResponse(it.inputStream)
            return response
        }
    }

    fun toParam(p: CommunicationProtos.Task.Param): CommunicationProtos.Task.Param {
        if (p.hasDependentTaskId()) {
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
