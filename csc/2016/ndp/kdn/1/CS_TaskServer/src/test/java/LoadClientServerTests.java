import client.MyClient;
import communication.Protocol;
import org.junit.Test;
import server.MyServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Created by dkorolev on 4/6/2016.
 */
public class LoadClientServerTests {
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

    private Protocol.Task getLongTaskWithABDependence(int taskId1, int taskId2) {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId1)).
                setB(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId2)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1000000).
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

    private Protocol.Task getTaskWithRandomDependency(List<Integer> taskIds, Random random) {
        synchronized (taskIds) {
            if (taskIds.size() == 0) {
                return getLongTask();
            } else {
                Integer taskId1 = taskIds.get(random.nextInt(taskIds.size()));
                Integer taskId2 = taskIds.get(random.nextInt(taskIds.size()));
                return getLongTaskWithABDependence(taskId1, taskId2);
            }
        }
    }

    private void addTaskIdToList(List<Integer> taskIds, int taskId) {
        synchronized (taskIds) {
            taskIds.add(taskId);
        }
    }

    @Test
    public void OneServer_OneClientSimpleAnotherLong_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        final int nTasks = 10;
        AtomicInteger tasksDone = new AtomicInteger();
        Thread thread1 = new Thread(() -> {
            MyClient myClient = new MyClient("1", "localhost", 8887);
            List<Integer> taskIds = new ArrayList<>(nTasks);
            for (int i = 0; i < nTasks; i++) {
                taskIds.add(myClient.submitTask(getSimpleTask()));
            }
            for (int i = 0; i < nTasks; i++) {
                myClient.subscribe(taskIds.get(i));
                tasksDone.getAndIncrement();
            }
        });

        Thread thread2 = new Thread(() -> {
            MyClient myClient = new MyClient("2", "localhost", 8887);
            List<Integer> taskIds = new ArrayList<>(nTasks);
            for (int i = 0; i < nTasks; i++) {
                taskIds.add(myClient.submitTask(getLongTask()));
            }
            for (int i = 0; i < nTasks; i++) {
                myClient.subscribe(taskIds.get(i));
                tasksDone.getAndIncrement();
            }
        });

        Thread thread3 = new Thread(() -> {
            MyClient myClient = new MyClient("3", "localhost", 8887);
            for (int i = 0; i < nTasks; i++) {
                myClient.getList();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join();
        thread3.join();

        assertEquals(2*nTasks, tasksDone.get());

        myServer.stop();
    }

    @Test
    public void OneServer_LongTasksWithDependence_SubscribeResult() throws InterruptedException {
        MyServer myServer = new MyServer(8887, 2, 2, 2);
        myServer.start();
        Thread.sleep(1000);
        final int nTasks = 10;
        AtomicInteger tasksDone = new AtomicInteger();
        final List<Integer> taskIds = new ArrayList<>(3*nTasks);
        final Random random = new Random();

        Thread thread1 = new Thread(() -> {
            MyClient myClient = new MyClient("1", "localhost", 8887);

            List<Integer> localTaskIds = new ArrayList<>(nTasks);
            for (int i = 0; i < nTasks; i++) {
                int taskId = myClient.submitTask(getTaskWithRandomDependency(taskIds, random));
                localTaskIds.add(taskId);
                addTaskIdToList(taskIds, taskId);
            }
            for (int i = 0; i < nTasks; i++) {
                myClient.subscribe(localTaskIds.get(i));
                tasksDone.getAndIncrement();
            }
        });

        Thread thread2 = new Thread(() -> {
            MyClient myClient = new MyClient("2", "localhost", 8887);

            List<Integer> localTaskIds = new ArrayList<>(nTasks);
            for (int i = 0; i < nTasks; i++) {
                int taskId = myClient.submitTask(getTaskWithRandomDependency(taskIds, random));
                localTaskIds.add(taskId);
                addTaskIdToList(taskIds, taskId);
            }
            for (int i = 0; i < nTasks; i++) {
                myClient.subscribe(localTaskIds.get(i));
                tasksDone.getAndIncrement();
            }
        });

        Thread thread3 = new Thread(() -> {
            MyClient myClient = new MyClient("3", "localhost", 8887);

            List<Integer> localTaskIds = new ArrayList<>(nTasks);
            for (int i = 0; i < nTasks; i++) {
                int taskId = myClient.submitTask(getTaskWithRandomDependency(taskIds, random));
                localTaskIds.add(taskId);
                addTaskIdToList(taskIds, taskId);
            }
            for (int i = 0; i < nTasks; i++) {
                myClient.subscribe(localTaskIds.get(i));
                tasksDone.getAndIncrement();
            }
        });


        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join();
        thread3.join();

        assertEquals(3*nTasks, tasksDone.get());

        myServer.stop();
    }
}
