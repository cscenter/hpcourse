import client.MyClient;
import communication.Protocol;
import org.junit.Test;
import server.MyServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by dkorolev on 4/5/2016.
 */
public class SimpleClientServerTests {

    private Protocol.Task getSimpleTask() {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1).
                build();
    }

    private Protocol.Task getLongTask() {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1000000).
                build();
    }

    @Test
    public void OneClientOneServer_SubmitSimple_Response() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getSimpleTask();
        int taskId = myClient.submitTask(task1);
        System.out.println(taskId);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_SubmitSimple_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getSimpleTask();
        int taskId = myClient.submitTask(task1);
        System.out.println(taskId);
        long taskResult = myClient.subscribe(taskId);
        System.out.println(taskResult);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_SubmitLong_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getLongTask();
        int taskId = myClient.submitTask(task1);
        System.out.println(taskId);
        long taskResult = myClient.subscribe(taskId);
        System.out.println(taskResult);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_SubmitList_GetList() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        List<Protocol.Task> tasks = new ArrayList<>();
        tasks.add(getSimpleTask());
        tasks.add(getLongTask());
        tasks.add(getSimpleTask());

        List<Integer> taskIds = new ArrayList<>();
        for (Protocol.Task task : tasks) {
            int taskId = myClient.submitTask(task);
            System.out.println(taskId);
            taskIds.add(taskId);
        }

        Thread.sleep(1000);
        List<Protocol.ListTasksResponse.TaskDescription> list = myClient.getList();
        System.out.println(list.size());
        assertEquals(3, list.size());
        Set<Integer> diffTaskIds = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Protocol.ListTasksResponse.TaskDescription taskDescription = list.get(i);
            assertTrue(taskIds.contains(taskDescription.getTaskId()));
            diffTaskIds.add(taskDescription.getTaskId());
            System.out.println(taskDescription.getTaskId() + " " +
                    taskDescription.getClientId() + " " +
                    taskDescription.hasResult());
        }
        assertEquals(3, diffTaskIds.size());
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_SubscribeResultOnNotExistedTask_ReturnNull() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        //Protocol.Task task1 = getSimpleTask();
        int taskId = 1000;
        System.out.println(taskId);
        Long taskResult = myClient.subscribe(taskId);
        assertEquals(null, taskResult);
        System.out.println(taskResult);
        myServer.stop();
    }
}
