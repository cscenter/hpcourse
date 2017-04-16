import client.Client;
import communication.Protocol;
import org.junit.Test;
import server.Server;

import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class SimpleTests {


    private Protocol.Task getSimpleTask() {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(1)).
                setB(Protocol.Task.Param.newBuilder().setValue(1)).
                setM(Protocol.Task.Param.newBuilder().setValue(1)).
                setP(Protocol.Task.Param.newBuilder().setValue(1)).
                setN(1).
                build();
    }

    private Protocol.Task getHardTask() {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setValue(12345)).
                setB(Protocol.Task.Param.newBuilder().setValue(23456)).
                setM(Protocol.Task.Param.newBuilder().setValue(34567)).
                setP(Protocol.Task.Param.newBuilder().setValue(45678)).
                setN(1000000000).
                build();
    }

    private Protocol.Task getDependTask(int depend1, int depend2, int depend3, int depend4) {
        return Protocol.Task.newBuilder().
                setA(Protocol.Task.Param.newBuilder().setDependentTaskId(depend1)).
                setB(Protocol.Task.Param.newBuilder().setDependentTaskId(depend2)).
                setM(Protocol.Task.Param.newBuilder().setDependentTaskId(depend3)).
                setP(Protocol.Task.Param.newBuilder().setDependentTaskId(depend4)).
                setN(1000000000).
                build();
    }

    public void submitHardTask() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = new Client("localhost", 1500);
                client.submitTask(getHardTask());
            }
        }).start();
    }

    public void subscribeTask() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = new Client("localhost", 1500);
                client.subscribe(5);
            }
        }).start();
    }

    public void printList(List<Protocol.ListTasksResponse.TaskDescription> tasks)
    {
        StringBuilder sb = new StringBuilder();
        for (Protocol.ListTasksResponse.TaskDescription task : tasks) {
            if (task.hasResult()) {
                sb.append(task.getTaskId() +  " - " + task.getResult());
            } else {
                sb.append(task.getTaskId() + " - NA");
            }
            sb.append(" || ");
        }
        System.out.println(sb.toString());
    }

    @Test
    public void SimpleTest() throws InterruptedException {

        Thread serverThread = new Thread(new Server("localhost", 1500));
        serverThread.start();

        Thread.sleep(1000);

        Client client = new Client("localhost", 1500);

        for (int i = 0; i < 4; i++)
            submitHardTask();

        Thread.sleep(100);
        client.getList();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = new Client("localhost", 1500);
                client.submitTask(getDependTask(1, 2, 3, 4));
            }
        }).start();

        Thread.sleep(100);
        client.getList();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Client client = new Client("localhost", 1500);
                client.submitTask(getDependTask(1, 2, 3, 5));
            }
        }).start();

        for (int i = 0; i < 4; i++)
            subscribeTask();

        long actual = client.subscribe(6);
        client.getList();

        assertEquals(0, actual);

    }
}
