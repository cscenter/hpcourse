import client.MyClient;
import communication.Protocol;
import org.junit.Test;
import server.MyServer;

import static org.junit.Assert.assertEquals;

/**
 * Created by dkorolev on 4/6/2016.
 */
public class DependencyClientServerTests {
    private Protocol.Task getSimpleTask() {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1).
                build();
    }

    private Protocol.Task getSimpleTaskWithDependence(int taskId) {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId)).
                setN(1).
                build();
    }

    private Protocol.Task getSimpleTaskWithABDependence(int taskId1, int taskId2) {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId1)).
                setB(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId2)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1).
                build();
    }

    private Protocol.Task getSimpleTaskWithMPDependence(int taskId1, int taskId2) {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId1)).
                setP(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId2)).
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
    public void OneClientOneServer_HasSimpleDependence_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 1, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getSimpleTask();
        int taskId1 = myClient.submitTask(task1);
        System.out.println(taskId1);

        Protocol.Task task2 = getSimpleTaskWithDependence(taskId1);
        int taskId2 = myClient.submitTask(task2);
        long taskResult2 = myClient.subscribe(taskId2);

        System.out.println(taskResult2);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_HasTwoDependence_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 1, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getSimpleTask();
        Protocol.Task task2 = getLongTask();
        int taskId1 = myClient.submitTask(task1);
        int taskId2 = myClient.submitTask(task2);
        System.out.println(taskId1);
        System.out.println(taskId2);

        Protocol.Task task3 = getSimpleTaskWithABDependence(taskId1, taskId2);
        int taskId3 = myClient.submitTask(task3);
        Long taskResult3 = myClient.subscribe(taskId3);

        System.out.println(taskResult3);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_HasTwoDependenceHandlingError_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 1, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        Protocol.Task task1 = getSimpleTask();
        Protocol.Task task2 = getLongTask();
        int taskId1 = myClient.submitTask(task1);
        int taskId2 = myClient.submitTask(task2);
        System.out.println(taskId1);
        System.out.println(taskId2);

        Protocol.Task task3 = getSimpleTaskWithMPDependence(taskId1, taskId2);
        int taskId3 = myClient.submitTask(task3);
        Long taskResult3 = myClient.subscribe(taskId3);

        System.out.println(taskResult3);
        myServer.stop();
    }

    @Test
    public void OneClientOneServer_HasTwoDependenceOneDoesNotExists_ReturnNull() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 1, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        MyClient myClient = new MyClient("1", "localhost", 8887);
        //Protocol.Task task1 = getSimpleTask();
        Protocol.Task task2 = getLongTask();
        int taskId1 = 10000;
        int taskId2 = myClient.submitTask(task2);
        System.out.println(taskId1);
        System.out.println(taskId2);

        Protocol.Task task3 = getSimpleTaskWithMPDependence(taskId1, taskId2);
        int taskId3 = myClient.submitTask(task3);
        Long taskResult3 = myClient.subscribe(taskId3);
        assertEquals(null, taskResult3);

        System.out.println(taskResult3);
        myServer.stop();
    }
}
