import server.*;

import java.io.IOException;

public class Main {
    static int port = 5276;
    public static void main(String[] args) {
        testTasksParallel();
    }

    static void calculateTask(long a, long b, long p, long m, long n) {
        System.out.print("Task with params a = " + a + " b = " + b + " p = " + p + " m = " + m + " n = " + n);
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        System.out.println(" res = " + a);
    }

    static void testNetwork() {
        try {
            Server server = new Server(port);
            Client client = new Client();
            client.runTests("127.0.0.1", port);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
    }

    static void testTasksParallel() {
        Server server = new Server();
//        for (int i = 0; i < 100; i++) {
//            new Thread(() -> {
//                server.submitTask(Task.Type.INDEPENDENT, 1, 1, 1, 1, 1000);
//            }).start();
//        }
        String clientId = "CLIENT_1_ID";
        final long[] id1 = new long[1];
        new Thread(() -> {
            id1[0] = server.submitTask(Task.Type.INDEPENDENT, clientId, 1, 2, 7, 31, 1000); //5
        }).start();

        final long[] id2 = new long[1];
        new Thread(() -> {
            id2[0] = server.submitTask(Task.Type.INDEPENDENT, clientId, 3, 5, 7, 31, 1000); //11
        }).start();

        final long[] id3 = new long[1];
        new Thread(() -> {
            id3[0] = server.submitTask(Task.Type.INDEPENDENT, clientId, 12, 4, 7, 31, 1000); //11
        }).start();

        final long[] id4 = new long[1];
        new Thread(() -> {
            id4[0] = server.submitTask(Task.Type.INDEPENDENT, clientId, 1, 20, 7, 31, 1000); //15
        }).start();

        //long id8 = server.submitTask(Task.Type.DEPENDENT, id4[0], id2[0], id3[0], id1[0], 1000); //3


//        long id5 = server.submitTask(Task.Type.INDEPENDENT, 1, 8, 3, 5, 1000); //4
//        long id6 = server.submitTask(Task.Type.INDEPENDENT, 2, 5, 7, 21, 1000); //5
//        long id7 = server.submitTask(Task.Type.INDEPENDENT, 3, 2, 1, 3, 1000); //1
//        System.out.println(id1[0] + " " + id2[0] + " " + id3[0] + " " + id4[0] + " " + id5 + " " + id6);
//        long id9 = server.submitTask(Task.Type.DEPENDENT, id7, id8, id6, id4[0], 1000); //3
//        long id10 = server.submitTask(Task.Type.DEPENDENT, id9, id5, id8, id4[0], 1000); //7
//        System.out.println("Subscribe on result of " + id10 + " = " + server.subscribeOnTaskResult(id10));
//        System.out.println("Subscribe on result of " + id9 + " = " + server.subscribeOnTaskResult(id9));
        /*while (id3[0] == 0) {
        }*/
        new Thread(() -> {
            try {
                Thread.currentThread().sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Subscribe on result of " + id3[0] + " = " + server.subscribeOnTaskResult(id3[0]));
        }).start();
    }

    static void testTasks() {
        Server server = new Server();
//        for (int i = 0; i < 100; i++) {
//            server.submitTask(Task.Type.INDEPENDENT, 1, 1, 1, 1, 1000);
//        }
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
        }
    }
}
