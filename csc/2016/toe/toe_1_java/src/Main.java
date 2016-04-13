import server.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        new Server().start();

        try {
            TrainClient client = new TrainClient();
            client.start();
            client.sendTask(42, 43, 44, 55, 66);
            client.sendTask(8, 15, 16, 23, 42);
            client.sendTask(18, 11, 19, 93, 666);
            client.sendTask(8, 15, 16, 23, 42);
            client.sendTask(18, 11, 19, 93, 666);
            client.listAllTasks();
            client.subscribeToTask(1);
            client.listAllTasks();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
