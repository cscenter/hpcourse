import client.Client;
import server.Server;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            new Server().start();
        } catch (IOException e) {
            System.out.println("Server port is not available");
            e.printStackTrace();
            System.exit(1);
        }
        try {
            new Client("localhost", Server.serverPort, "client.Client").start();
        } catch (IOException e) {
            System.out.println("Can not connect to server");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
