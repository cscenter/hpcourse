import communication.Parameter;
import server.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        new Server().start();

        try {
            TrainClient client = new TrainClient();
            client.start();
            client.sendTask(new Parameter(42), new Parameter(43), new Parameter(44), new Parameter(55), 66);
            client.sendTask(new Parameter(8), new Parameter(15), new Parameter(16), new Parameter(23), 42);
            client.sendTask(new Parameter(18), new Parameter(11), new Parameter(19), new Parameter(93), 666);
            client.sendTask(new Parameter(8), new Parameter(15), new Parameter(16), new Parameter(23), 42);
            client.sendTask(new Parameter(18), new Parameter(11), new Parameter(19), new Parameter(93), 666);
            client.listAllTasks();
            client.subscribeToTask(1);
            client.listAllTasks();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
