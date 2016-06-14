package server;

import server.storage.TaskStorage;
import server.thread.*;

import static communication.Protocol.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Base class of server. Listens socket and creates tasks.
 * @author Dmitriy Tseyler
 */
public class Server extends Thread {

    private static final Logger log = Logger.getLogger(Server.class.getName());

    private final int port;
    private final String host;
    private final TaskStorage storage;

    private Server(String host, int port) {
        this.port = port;
        this.host = host;

        storage = new TaskStorage();
    }

    @Override
    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName(host));
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = serverSocket.accept();
                processClientSocket(clientSocket);
            }
        } catch (IOException e){
            log.warning("Error on server side: " + e.getMessage());
        }
    }

    private void processClientSocket(Socket socket) throws IOException {
        ServerRequest request = ServerRequest.parseDelimitedFrom(socket.getInputStream());
        new TaskStarter(socket, request, storage).start();
    }

    public static void main(String[] args) {
        if (args.length != 2)
            throw new IllegalArgumentException("Must be host and port in arguments");

        new Server(args[0], Integer.valueOf(args[1])).start();
    }
}
