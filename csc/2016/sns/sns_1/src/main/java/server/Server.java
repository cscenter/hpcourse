package server;

import communication.Protocol;
import server.processors.BaseTaskProcessor;
import server.processors.BaseTaskProcessorFactory;
import server.processors.NoProcessorForTaskException;
import util.ConcurrentStorage;
import util.ProtocolUtils;
import util.TaskAndResult;
import util.ThreadPool;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */

public class Server implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());
    final ConcurrentStorage<TaskAndResult> concurrentStorage = new ConcurrentStorage<>();
    private final ServerSocket serverSocket;
    private final ThreadPool threadPool;

    /**
     * @param port
     * @throws IOException if an I/O error occurs when opening the socket for server
     */
    public Server(final int port) throws IOException {
        serverSocket = new ServerSocket(port);
        threadPool = new ThreadPool();
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.warning("Can't close server socket");
        }
    }

    @Override
    public void run() {
        LOGGER.info("Server starting...");
        while (!serverSocket.isClosed()) {
            try {
                LOGGER.info("Wait for new socket");
                final Socket socket = serverSocket.accept();
                LOGGER.info("Accept socket");

                threadPool.execute(() -> {
                    while (!socket.isClosed()) {
                        final Protocol.WrapperMessage message;
                        try {
                            message = ProtocolUtils.readWrappedMessage(socket);

                            if (!message.hasRequest()) {
                                LOGGER.warning("Got message without request. Ignore it and continue work");
                                continue;
                            }

                            final Protocol.ServerRequest request = message.getRequest();
                            LOGGER.info("Server read request: " + request.getClientId() + ' ' + request.getRequestId());

                            final BaseTaskProcessor taskProcessor = new BaseTaskProcessorFactory(concurrentStorage, socket, request).getProcessor();
                            taskProcessor.run();
                        } catch (IOException e) {
                            LOGGER.warning("Can't read message from socket");
                        } catch (NoProcessorForTaskException e) {
                            LOGGER.warning("No processor for retrieved task");
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    LOGGER.info("Server detect that client socket closed");
                });
            } catch (IOException e) {
                LOGGER.warning("Error while waiting for client socket, retry. Error: " + e);
            }
        }
        LOGGER.info("Server stopped");
    }
}
