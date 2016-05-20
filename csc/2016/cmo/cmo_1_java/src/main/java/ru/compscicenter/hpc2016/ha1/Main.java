package ru.compscicenter.hpc2016.ha1;

import ru.compscicenter.hpc2016.ha1.client.Client;
import ru.compscicenter.hpc2016.ha1.server.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        int serverPort = 4242;

        try {
            new Server(serverPort);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        try {
            Client client1 = new Client("localhost", serverPort, "#1");
            client1.sendSubmitTaskRequest(42, 20, 30, 0, 0);
            client1.sendSubscribeRequest(1);
            client1.sendSubscribeRequest(2);
            client1.sendSubscribeRequest(1);
            Client client2 = new Client("localhost", serverPort, "#2");
            client2.sendSubmitTaskRequest(10, 20, 40, 1, 1000000000);
            client2.sendSubscribeRequest(2);
            client2.sendSubscribeRequest(1);
            client2.sendSubscribeRequest(2);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}