package server;

import protocol.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ServerRequestThread extends Thread {
    Socket socket;

    public ServerRequestThread(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (InputStream is = socket.getInputStream()) {
            while (true) {
                int messageSize = is.read();

                if (messageSize < 0) {
                    continue;
                }

                byte[] buffer = new byte[messageSize];
                is.read(buffer);

                Protocol.ServerRequest serverRequest = Protocol.ServerRequest.parseFrom(buffer);

                if (serverRequest.hasSubmit()) {
                    new SubmitTaskThread(socket, serverRequest).start();
                }
                if (serverRequest.hasSubscribe()) {
                    new SubscribeTaskThread(socket, serverRequest).start();
                }
                if (serverRequest.hasList()) {
                    new TaskListThread(socket, serverRequest).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
