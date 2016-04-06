package server;

import server.storage.TaskStorage;

import static communication.Protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * @author Dmitriy Tseyler
 */
public class Server extends Thread {

    private static final Logger log = Logger.getLogger(Server.class.getName());

    private static long id = 0;

    private final int port;
    private final TaskStorage storage;

    public Server(int port) {
        this.port = port;
        storage = new TaskStorage();
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            //noinspection ???
            while (true) {
                Socket clientSocket = serverSocket.accept();
                processClientSocket(clientSocket);
            }
        } catch (IOException e){
            log.warning("Error on server side: " + e.getMessage());
        }
    }

    private void processClientSocket(Socket socket) throws IOException {
        InputStream stream = socket.getInputStream();
        int size = stream.read();
        byte[] buffer = new byte[size];
        int result = stream.read(buffer);
        if (result == -1) throw new IOException("Can't read data from client socket");
        ServerRequest request = ServerRequest.PARSER.parseFrom(buffer);
        processRequest(request);
    }

    private void processRequest(ServerRequest request) {

    }
}
