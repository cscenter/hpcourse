package org.compscicenter.kozmirchuk.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;


public class Server implements Runnable {

    private final int port;
    private final Logger logger = LoggerFactory.getLogger(Server.class);

    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        new Thread(new Server(5555), "Server").start();
    }

    public void run() {

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(1000);
            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    logger.error("Handling new client");
                    new Thread(new ClientHandler(client), "Client Handler").start();
                } catch (IOException e) {
                    //logger.error(e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }

    }
}
