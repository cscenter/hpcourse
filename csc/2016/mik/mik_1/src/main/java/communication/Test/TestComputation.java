package communication.Test;

import communication.Client.Client;
import communication.Protocol;
import communication.Task;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class TestComputation {
    public static void main(String[] args) {

        Client client = new Client("client1", "localhost", 10918);

        try {
            client.SubmitTask(new Task(buildValue(1), buildValue(2), buildValue(3), buildValue(239), (long) 1e9));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Protocol.Task.Param buildValue(long value) {
        return Protocol.Task.Param.newBuilder().setValue(value).build();
    }
}
