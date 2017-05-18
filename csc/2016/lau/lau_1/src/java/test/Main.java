package test;

import server.*;

import java.io.IOException;

public class Main {
    private static int port = 5276;
    public static void main(String[] args) {
        //testNetwork();
        printTestResults();
        //testTasksParallel();
    }

    private static void testNetwork() {
        startServer(port);
        startClient(port);
    }

    private static void startServer(int port) {
        new Thread(() -> {
            try {
                new Server(port).startListening();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void startClient(int port) {
        new Thread(() -> {
            AsynchronousClient client = new AsynchronousClient();
            client.runTests("127.0.0.1", port);
        }).start();
    }

    static void printTestResults() {
        calculateTask(1, 2, 7, 31, 1000);
        calculateTask(1, 20, 7, 31, 1000);
        calculateTask(3, 5, 7, 31, 1000);
        calculateTask(12, 4, 7, 31, 1000);
        calculateTask(4, 3, 2, 3, 1000);
        calculateTask(3, 2, 1, 3, 1000);
        calculateTask(1, 1, 1, 5, 1000);
        calculateTask(15, 11, 11, 5, 1000);
        calculateTask(5, 11, 11, 15, 1000);
        calculateTask(3, 12, 17, 5, 1000); //1
        calculateTask(3, 1, 4, 21, 1000);  //8
        calculateTask(2, 5, 7, 21, 1000);  //5
        calculateTask(1, 8, 3, 5, 1000);   //4
        calculateTask(1, 3, 5, 15, 1000);  //3
        calculateTask(3, 4, 3, 15, 1000);  //7
        calculateTask(8, 5, 7, 3, 1000);  //2
        calculateTask(8, 5, 3, 7, 1000);  //1
        calculateTask(5, 3, 7, 8, 1000);  //0
        calculateTask(8, 3, 7, 5, 1000);  //3
        calculateTask(8, 5, 3, 7, 1000);  //1
        calculateTask(8, 3, 5, 7, 1000);  //6
        calculateTask(5, 7, 3, 8, 1000);  //0

        calculateTask(3, 1, 4, 21, 1000001);  //19
        calculateTask(2, 5, 9, 21, 1000001);  //8
        calculateTask(1, 8, 6, 31, 1000001);   //9
        calculateTask(7, 3, 5, 101, 1000001);  //38
        calculateTask(19, 8, 9, 38, 10000001);  //34
    }

    private static void calculateTask(long a, long b, long p, long m, long n) {
        System.out.print("Task with params a = " + a + " b = " + b + " p = " + p + " m = " + m + " n = " + n);
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        System.out.println(" res = " + a);
    }

    static void testTasksParallel() {
        Server server = new Server();
//        for (int i = 0; i < 100; i++) {
//            new Thread(() -> {
//                server.submitTask(Task.Type.INDEPENDENT, 1, 1, 1, 1, 1000);
//            }).start();
//        }

        String clientId = "ClientTest1";

        int id1 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 1),
                new TaskParam(TaskParam.Type.VALUE, 2),
                new TaskParam(TaskParam.Type.VALUE, 7),
                new TaskParam(TaskParam.Type.VALUE, 31),
                1000); //5

        int id2 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 3),
                new TaskParam(TaskParam.Type.VALUE, 5),
                new TaskParam(TaskParam.Type.VALUE, 7),
                new TaskParam(TaskParam.Type.VALUE, 31),
                1000); //11

        int id3 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 12),
                new TaskParam(TaskParam.Type.VALUE, 4),
                new TaskParam(TaskParam.Type.VALUE, 7),
                new TaskParam(TaskParam.Type.VALUE, 31),
                1000); //11

        int id4 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 1),
                new TaskParam(TaskParam.Type.VALUE, 20),
                new TaskParam(TaskParam.Type.VALUE, 7),
                new TaskParam(TaskParam.Type.VALUE, 31),
                1000); //15

        int id5 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 1),
                new TaskParam(TaskParam.Type.VALUE, 8),
                new TaskParam(TaskParam.Type.VALUE, 3),
                new TaskParam(TaskParam.Type.VALUE, 5),
                1000); //4

        int id6 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 2),
                new TaskParam(TaskParam.Type.VALUE, 5),
                new TaskParam(TaskParam.Type.VALUE, 7),
                new TaskParam(TaskParam.Type.VALUE, 21),
                1000); //5

        int id7 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 3),
                new TaskParam(TaskParam.Type.VALUE, 2),
                new TaskParam(TaskParam.Type.VALUE, 1),
                new TaskParam(TaskParam.Type.VALUE, 3),
                1000); //1

        int id8 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.TASK_ID, id4),
                new TaskParam(TaskParam.Type.TASK_ID, id2),
                new TaskParam(TaskParam.Type.TASK_ID, id3),
                new TaskParam(TaskParam.Type.TASK_ID, id1),
                1000); //3

        int id9 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.TASK_ID, id7),
                new TaskParam(TaskParam.Type.TASK_ID, id8),
                new TaskParam(TaskParam.Type.TASK_ID, id6),
                new TaskParam(TaskParam.Type.TASK_ID, id4),
                1000); //3

        int id10 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.TASK_ID, id9),
                new TaskParam(TaskParam.Type.TASK_ID, id5),
                new TaskParam(TaskParam.Type.TASK_ID, id8),
                new TaskParam(TaskParam.Type.TASK_ID, id4),
                1000); //7

        int id11 = server.submitTask(
                clientId,
                new TaskParam(TaskParam.Type.VALUE, 2),
                new TaskParam(TaskParam.Type.VALUE, 5),
                new TaskParam(TaskParam.Type.TASK_ID, id10), //7
                new TaskParam(TaskParam.Type.VALUE, 21),
                1000); //5

        testResult(server, id1, 5);
        testResult(server, id2, 11);
        testResult(server, id3, 11);
        testResult(server, id4, 15);
        testResult(server, id5, 4);
        testResult(server, id6, 5);
        testResult(server, id7, 1);
        testResult(server, id8, 3);
        testResult(server, id9, 3);
        testResult(server, id10, 7);
        testResult(server, id11, 5);

        for (Task task : server.getTasksList()) {
            System.out.println(task);
        }

    }

    private static void testResult(Server server, int taskId, long result) {
        System.out.println("Subscribe on result of " + taskId + " expected result: " + result
                + " got: " + server.subscribeOnTaskResult(taskId));
        assert (server.subscribeOnTaskResult(taskId) == result);
    }

    static void testTasks() {
        Server server = new Server();
//        for (int i = 0; i < 100; i++) {
//            server.submitTask(Task.Type.INDEPENDENT, 1, 1, 1, 1, 1000);
//        }
        /*
        String clientId = "CLIENT_1_ID";
        long id1 = server.submitTask(Task.Type.INDEPENDENT, clientId, 1, 2, 7, 31, 1000); //5
        long id2 = server.submitTask(Task.Type.INDEPENDENT, clientId, 3, 5, 7, 31, 1000); //11
        long id3 = server.submitTask(Task.Type.INDEPENDENT, clientId, 12, 4, 7, 31, 1000); //11
        long id4 = server.submitTask(Task.Type.INDEPENDENT, clientId, 1, 20, 7, 31, 1000); //15
        long id5 = server.submitTask(Task.Type.INDEPENDENT, clientId, 1, 8, 3, 5, 1000); //4
        long id6 = server.submitTask(Task.Type.INDEPENDENT, clientId, 2, 5, 7, 21, 1000); //5
        long id7 = server.submitTask(Task.Type.INDEPENDENT, clientId, 3, 2, 1, 3, 1000); //1
        System.out.println(id1 + " " + id2 + " " + id3 + " " + id4 + " " + id5 + " " + id6);
        long id8 = server.submitTask(Task.Type.DEPENDENT, clientId, id4, id2, id3, id1, 1000); //3
        long id9 = server.submitTask(Task.Type.DEPENDENT, clientId, id7, id8, id6, id4, 1000); //3
        long id10 = server.submitTask(Task.Type.DEPENDENT, clientId,  id9, id5, id8, id4, 1000); //7
        System.out.println("Subscribe on result of " + id10 + " = " + server.subscribeOnTaskResult(id10));
        System.out.println("Subscribe on result of " + id9 + " = " + server.subscribeOnTaskResult(id9));
        System.out.println("Subscribe on result of " + id3 + " = " + server.subscribeOnTaskResult(id3));

        try {
            Thread.currentThread().sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        for (Task x : server.getTasksList()) {
            System.out.println(x.toString());
        }*/
    }
}
