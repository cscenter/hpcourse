package server

import communication.CommunicationProtos
import org.junit.ClassRule
import org.junit.Test
import util.client.ServerResource
import util.client.TaskWorker


/**
 * @author Sergey Voytovich (voytovich.sergeey@gmail.com) on 10.04.16
 */
class ServerTest {

    companion object {

        val port = 4774

        @ClassRule
        @JvmField
        val server = ServerResource(port)
    }


    @Test
    fun submit_task_test() {
        println("haha")
        val client = TaskWorker(port, "first-client")
        client.submit(param(100), param(100), param(100), param(100), 100)
        val response = client.list()
        print(response)
    }

    fun param(p: Long): CommunicationProtos.Task.Param {
        return CommunicationProtos.Task.Param.newBuilder()
                .setValue(p)
                .build()
    }

    fun dependentTask(id: Int): CommunicationProtos.Task.Param {
        return CommunicationProtos.Task.Param.newBuilder()
                .setDependentTaskId(id)
                .build()
    }

}
