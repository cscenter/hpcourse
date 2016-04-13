package server

import communication.CommunicationProtos
import org.junit.After
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import util.client.ServerResource
import util.client.TaskWorker


class ServerTest {

    companion object {
        val port = 4774
        @ClassRule
        @JvmField
        val server = ServerResource(port)
    }

    @After
    fun separate_tests_outputs() {
        println("------------------------------")
    }

    @Test
    fun submit_task_test() {
        val client = TaskWorker(port, "first-client")
        val str = client.submit(param(100), param(15), param(23), param(11), 10)
        Thread.sleep(1000)
        val response = client.list()
        Assert.assertEquals(response.status, CommunicationProtos.Status.OK)
        for (task in response.tasksList) {
            if (str.submittedTaskId == task.taskId) {
                Assert.assertEquals(calculate(100, 15, 23, 11, 10), task.result)
            }
        }
    }

    @Test(timeout = 1000)
    fun list_running_task_test() {
        val client = TaskWorker(port, "first-client")
        client.submit(param(100), param(15), param(23), param(11), Long.MAX_VALUE)
        val response = client.list()
        Assert.assertEquals(response.status, CommunicationProtos.Status.OK)
    }

    @Test(timeout = 100000)
    fun subscribe_test() {
        val client = TaskWorker(port, "first-client")
        val submitResponse = client.submit(param(100), param(15), param(23), param(11), Integer.MAX_VALUE.toLong() / 4)
        val response = client.subscribe(submitResponse.submittedTaskId)
        Assert.assertTrue(response.status == CommunicationProtos.Status.OK)
    }

    @Test
    fun dependent_task_test() {
        val client = TaskWorker(port, "first-client")
        val firstSubmit = client.submit(param(100), param(15), param(23), param(11), Integer.MAX_VALUE.toLong() / 4)
        val secondSubmit = client.submit(param(100), param(15), param(23), dependentTask(firstSubmit.submittedTaskId), 10)
        val secondTaskResult = client.subscribe(secondSubmit.submittedTaskId).value
        val firstTaskResult = client.subscribe(firstSubmit.submittedTaskId).value
        Assert.assertEquals(secondTaskResult, calculate(100, 15, 23, firstTaskResult, 10))
    }

    @Test(timeout = 1000)
    fun long_running_task_not_stops_work_test() {
        val client = TaskWorker(port, "first-client")
        client.submit(param(100), param(15), param(23), param(11), Long.MAX_VALUE)

        val id = client.submit(param(100), param(15), param(23), param(11), 10).submittedTaskId
        val result = client.subscribe(id)
        Assert.assertTrue(result.status == CommunicationProtos.Status.OK)
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

    fun calculate(_a: Long, _b: Long, _p: Long, _m: Long, _n: Long): Long {
        var a = _a
        var b = _b
        var p = _p
        var m = _m
        var n = _n
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a
    }
}
