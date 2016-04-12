package server;

import java.net.*;
import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Usage: java server.Server <port number>");
            System.exit(1);
        }

        int portNumber = Integer.parseInt(args[0]);

        final TaskManager taskManager = new TaskManager();

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                new ServerThread(serverSocket.accept(), taskManager).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}