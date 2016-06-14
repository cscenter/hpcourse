package hw;


import java.net.ServerSocket;

/**
 * Created by Егор on 14.04.2016.
 */
public class Server extends Thread {
    private int portNumber;
    private TaskMap taskMap = new TaskMap();

    public Server(int port) {
        portNumber = port;
    }

    public static void main(String[] args) {
        System.out.println("Server is running: ");
        new Server(44444).start();
    }

    @Override
    public void run() {
        try {
            ServerSocket server = new ServerSocket(portNumber);
            while (true) {
                new ThreadForTask(server.accept(), taskMap).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

