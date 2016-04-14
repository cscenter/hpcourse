package Server;


import communication.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by scorpion on 14.04.16.
 */

public class Server extends Thread{

    public Server(int serverPort) throws IOException {
        this.serverPort = serverPort;
        serverSocket = new ServerSocket(serverPort);
    }

    @Override
    public void run() {
        try {
            while (true) {
                Socket connection = serverSocket.accept();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Protocol.ServerRequest request = Protocol.ServerRequest.parseDelimitedFrom(connection.getInputStream());
                            if (request.hasSubmit()) {

                            }
                            if (request.hasSubscribe()) {

                            }
                            if (request.hasList()) {

                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                connection.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            System.err.println("Error with serever socket!");
            e.printStackTrace();
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    private int serverPort;
    private ServerSocket serverSocket;

}
