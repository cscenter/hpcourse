package server;

import java.net.*;
import java.io.*;

public class Server {
    public static void main(String[] args) throws IOException {

        int portNumber = 8889;
        if (args.length == 1) {
            portNumber = Integer.parseInt(args[0]);
        }

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