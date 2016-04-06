package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class MyServer {
    private final int port;
    private final ExecutorService executorService;
    private final TaskManager taskManager;
    private final ExecutorService mainThreadExecutor;
    private volatile boolean executing;

    public MyServer(int port, int nThreadsForRequests, int nThreadsForTasks, int delayInSecondsForTasks) {
        this.port = port;
        this.executorService = Executors.newFixedThreadPool(nThreadsForRequests);
        this.taskManager = new TaskManager(nThreadsForTasks, delayInSecondsForTasks);
        this.mainThreadExecutor = Executors.newSingleThreadExecutor();
        this.executing = true;
    }

    public void start() {
        mainThreadExecutor.submit(() -> {
            //boolean listening = true;
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setSoTimeout(100);
                while (executing) {
                    try {
                        Socket socket = serverSocket.accept();
                        executorService.submit(new MyServerRunnable(socket, taskManager));
                    } catch (SocketTimeoutException ignored) {
                        /*if(!executing) {
                            System.out.println("Server finished");
                        }*/
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Could not listen on port " + port);
                //System.exit(-1);
            }
        });
    }

    public void stop() {
        try {
            executing = false;
            mainThreadExecutor.shutdown();
            mainThreadExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            taskManager.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
