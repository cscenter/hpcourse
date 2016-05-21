package server;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public class ClientResolver implements Runnable {
    private final Socket clientSocket;
    private final Server server;
    public ClientResolver(Server server, Socket clientSocket) {
        this.server = server;
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();
        ) {
            while (!Thread.currentThread().isInterrupted()) {
                Protocol.WrapperMessage message = Protocol.WrapperMessage.parseDelimitedFrom(inputStream);
                new Thread(new RequestResolver(server, message.getRequest(), outputStream)).start();
            }
        } catch (IOException e) {
            Thread.currentThread().interrupt();
        }
    }
}
