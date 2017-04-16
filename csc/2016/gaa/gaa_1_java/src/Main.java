import Client.Client;
import communication.Protocol.*;
import util.SynchronizedInt;

import java.io.IOException;
import java.util.ArrayList;
import Server.Server;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Server server;
        int port = 10918;
        try {
            server = new Server(port);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Client client;
        try {
            client = new Client(port);
            client.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
