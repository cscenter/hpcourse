package ru.csc.roman_fedorov;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by roman on 15.04.2016.
 */

public class Server {
    public static final int DEFAULT_PORT = 5560;
    public static final Map<Integer, CustomTaskDescription> m =
            Collections.synchronizedMap(new HashMap<Integer, CustomTaskDescription>());
    public static final AtomicInteger idCounter = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        int portNumber = args.length == 1 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                new SocketConnectionThread(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            System.err.println("Could not listen on port " + portNumber);
            System.exit(-1);
        }
    }
}
