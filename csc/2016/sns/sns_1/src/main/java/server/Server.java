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
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */

public final class Server {
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());
    final ConcurrentStorage<TaskAndResult> concurrentStorage = new ConcurrentStorage<>();
    private final Set<Socket> openSockets = new HashSet<>();
    private final ServerSocket serverSocket;
    private final ThreadPool threadPool;
    private final Thread waitSocketsThread = new Thread(new WaitSocketThread());
    private final Thread readSocketsThread = new Thread(new ReadSocketThread());

    /**
     * @param port
     * @throws IOException if an I/O error occurs when opening the socket for server
     */
    public Server(final int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1000);
        threadPool = new ThreadPool();
        waitSocketsThread.start();
        readSocketsThread.start();
    }

    public void stop() {
        waitSocketsThread.interrupt();
        readSocketsThread.interrupt();
        try {
            waitSocketsThread.join();
            readSocketsThread.join();
        } catch (InterruptedException e) {
            LOGGER.info("Join was interrupted");
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            LOGGER.info("Can't close server socket");
        }
    }

    private class WaitSocketThread implements Runnable {

        @Override
        public void run() {
            LOGGER.info("Server starting...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Socket socket = serverSocket.accept();
                    synchronized (openSockets) {
                        openSockets.add(socket);
                    }
                    LOGGER.info("Socket accepted");
                } catch (SocketTimeoutException ste) {
                    //Nothing to do
                } catch (IOException e) {
                    LOGGER.warning("Can't accept socket: " + e);
                }
            }
        }
    }

    private class ReadSocketThread implements Runnable {
        @Override
        public void run() {
            LOGGER.info("Server starting...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final Set<Socket> forRemove = new HashSet<>();
                    final Set<Socket> openSocketsCopy = new HashSet<>();

                    synchronized (openSockets) {
                        openSocketsCopy.addAll(openSockets);
                    }

                    for (final Socket socket : openSocketsCopy) {
                        if (socket.isClosed()) {
                            forRemove.add(socket);
                            continue;
                        }
                        final Protocol.WrapperMessage message = ProtocolUtils.readWrappedMessage(socket);

                        if (message == null) {
                            continue;
                        }

                        if (!message.hasRequest()) {
                            LOGGER.warning("Got message without request. Ignore it and continue work");
                            continue;
                        }

                        final Protocol.ServerRequest request = message.getRequest();
                        LOGGER.info("Server read request: " + request.getClientId() + ' ' + request.getRequestId());

                        final BaseTaskProcessor taskProcessor = new BaseTaskProcessorFactory(concurrentStorage, socket, request).getProcessor();
                        threadPool.execute(taskProcessor);
                    }

                    synchronized (openSockets) {
                        openSockets.removeAll(forRemove);
                    }
                } catch (IOException e) {
                    LOGGER.warning("Can't read message from socket");
                } catch (NoProcessorForTaskException e) {
                    LOGGER.warning("No processor for retrieved task");
                } catch (InterruptedException e) {
                    return;
                }
            }
            LOGGER.info("Server detect that client socket closed");
        }
    }
}
