package server;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeoutException;

/**
 * Created by Helen on 10.04.2016.
 */
public class Server {
    private int port = 12345;
    private TaskManager taskManager = new TaskManager();
    private boolean executing = true;
    public Server (){}
    public Server(int port){
        this.port = port;
    }

    public void start(){
        this.executing = true;
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (executing) {
                Socket socket = serverSocket.accept();
                new Thread(new ExecuteRequest(socket, taskManager)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] argv){
        if(argv.length > 1){
            new Server(Integer.parseInt(argv[1])).start();
        }
        else
            new Server().start();
    }

    public void stop(){
        this.executing = false;
    }
}
