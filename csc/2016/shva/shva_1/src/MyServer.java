import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MyServer extends Thread {

    private static final Logger LOG = Logger.getLogger("Server");
    private final int port;
    private int taskId = 0;

    public MyServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("Server is running");
            while (true) {
                final Socket socket = serverSocket.accept();
                LOG.info("New request");
                Thread thread = new TaskThread(socket, taskId);
                thread.start();
                taskId++;
            }
        } catch (IOException e) {
            LOG.warning("Server error : port is unavailable");
        } finally {
            LOG.info("Server finished");
        }
    }
}
