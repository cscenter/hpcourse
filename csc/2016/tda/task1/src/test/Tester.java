package test;

import client.Client;
import server.Server;

/**
 * @author Dmitriy Tseyler
 */
class Tester {
    public static void main(String[] args) {
        String[] arguments = new String[] {"localhost", "8080"};
        Server.main(arguments);
        System.out.println("Server started...");
        Client.main(arguments);
        System.out.println("Client started...");
    }
}
