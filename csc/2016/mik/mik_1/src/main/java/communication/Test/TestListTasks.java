package communication.Test;

import communication.Client.Client;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class TestListTasks {

    public static void main(String[] args) {
        Client client = new Client("client3", "localhost", 10918);

        try {
            System.out.println(client.ListTasks());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
