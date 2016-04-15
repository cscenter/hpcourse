package client;

import protocol.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.Socket;

public class ListenClientThread extends Thread {
    private Socket socket;

    public ListenClientThread(Socket socket) {
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
                Protocol.ServerResponse response = Protocol.ServerResponse.parseFrom(buffer);

                System.out.println(response);
            }
        } catch (InterruptedIOException e) {
            System.out.println("Client is right shutdown");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Client is shutdown");
        }
    }
}
