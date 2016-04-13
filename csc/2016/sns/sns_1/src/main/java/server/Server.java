package server;

import communication.Protocol;
import server.processors.BaseTaskProcessor;
import server.processors.BaseTaskProcessorFactory;
import server.processors.NoProcessorForTaskException;
import util.ThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */

//TODO: add possibility to stop server
public class Server extends Thread {

    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    private final ServerSocket serverSocket;
    private final ThreadPool threadPool;
    private boolean running;

    /**
     * @param port
     * @throws IOException if an I/O error occurs when opening the socket for server
     */
    public Server(final int port) throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = new ThreadPool();
        running = true;
    }

    @Override
    public void interrupt() {
        running = false;
    }

    @Override
    public void run() {
        LOGGER.info("Server starting...");
        while (running) {
            try {
                LOGGER.info("Wait for new socket");
                final Socket socket = serverSocket.accept();
                final InputStream inputStream = socket.getInputStream();
                LOGGER.info("Accept socket");

                while (!socket.isClosed()) {

                    LOGGER.info("Socket not closed so read request");
                    final int size = inputStream.read();
                    LOGGER.info("Message size:" + size);
                    final byte[] message = new byte[size];
                    inputStream.read(message);

                    final Protocol.ServerRequest request = Protocol.ServerRequest.parseFrom(message);
                    LOGGER.info("Server read request: " + request.getClientId() + " " + request.getRequestId());

                    try {
                        final BaseTaskProcessor taskProcessor = new BaseTaskProcessorFactory(socket, request).getProcessor();
                        threadPool.execute(taskProcessor);
                    } catch (NoProcessorForTaskException e) {
                        LOGGER.warning("Get message with unknown type, ignore it:" + e);
                    }

                }
            } catch (IOException e) {
                LOGGER.warning("Error while waiting for client socket, retry. Error: " + e);
            }
        }
        LOGGER.info("Server stopped");
    }
}
