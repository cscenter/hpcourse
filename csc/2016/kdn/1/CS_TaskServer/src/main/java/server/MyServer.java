package server;

import concurrent.MyExecutorService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class MyServer {
    private final int port;
    private final MyExecutorService executorService;
    private final TaskManager taskManager;
    private final MyExecutorService mainThreadExecutor;
    private volatile boolean executing;

    public MyServer(int port, int nThreadsForRequests, int nThreadsForTasks, int delayInSecondsForTasks) {
        this.port = port;
        this.executorService = MyExecutorService.newFixedThreadPool(nThreadsForRequests);
        this.taskManager = new TaskManager(nThreadsForTasks, delayInSecondsForTasks);
        this.mainThreadExecutor = MyExecutorService.newSingleThreadExecutor();
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

            return null;
        });
    }

    public void stop() {
        try {
            executing = false;
            mainThreadExecutor.awaitTerminationSkipQueried();
            taskManager.stop();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
