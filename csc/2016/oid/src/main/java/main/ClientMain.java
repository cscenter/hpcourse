package main;

import client.Client;

import java.io.IOException;

public class ClientMain {
    public static void main(String[] args) throws IOException {

        Client client = new Client("localhost", 8080);

        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);
        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);
        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);
        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);
        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);
        client.sendSubmitTaskRequest(1, 2, 3, 4, 5);

        client.sendSubscribeTaskRequest(3);
        client.sendSubscribeTaskRequest(4);
        client.sendSubscribeTaskRequest(1);

        client.sendListTaskRequest();
    }
}
