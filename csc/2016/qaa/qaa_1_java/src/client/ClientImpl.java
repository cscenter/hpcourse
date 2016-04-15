package client;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public class ClientImpl implements Client {
    // not complete.
    private static void printUsage() {
        System.err.println("Usage: java ClientImpl <host name> <port number>");
    }
    public static void main(String[] args) {
        if (args.length != 2) {
            printUsage();
            System.exit(1);
        }
        String hostName = args[0];
        int portNumber = Integer.parseInt(args[1]);
        try (
                Socket clientSocket = new Socket(hostName, portNumber);
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();
        ) {
            while (true) {
                Protocol.ServerResponse response = Protocol.ServerResponse.parseFrom(inputStream);
                System.out.println(response.toString());
            }
        } catch (UnknownHostException e) {
            System.err.println("Connection error, cause: " + e.getMessage());
            printUsage();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    public void submitTaskResponse(Protocol.ServerResponse response) {

    }

    @Override
    public void subscribeResponse(Protocol.ServerResponse response) {

    }

    @Override
    public void listTasksResponse(Protocol.ServerResponse response) {

    }
}
