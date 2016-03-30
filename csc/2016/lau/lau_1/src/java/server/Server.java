package server;

import communication.ProtocolProtos;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server {
    private TaskList taskList;
    int port;

    public Server(int port) throws IOException {
        this.port = port;
        taskList = new TaskList();
        startListening();
    }

    private void startListening() throws IOException {
        new Thread(() -> {
            try (ServerSocket socket = new ServerSocket(port)) {
                System.out.println("Server started");
                Socket clientSocket = socket.accept();
                processClient(clientSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processClient(Socket clientSocket) {
        new Thread(() -> {
            System.out.println("Server: got client");
            try (InputStream inputStream = clientSocket.getInputStream()) {
                while (true) {
                    ProtocolProtos.WrapperMessage msg = readMessage(inputStream);
                    if (msg.hasRequest()) {
                        System.out.println("Server: get request");
                    }
                    if (msg.hasResponse()) {
                        System.out.println("Server: get response?");
                    }
                    if (!msg.isInitialized()) {
                        System.out.println("Server: got not initialized msg");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private ProtocolProtos.WrapperMessage readMessage(InputStream inputStream) throws IOException {
        ProtocolProtos.WrapperMessage msg = ProtocolProtos.WrapperMessage.parseFrom(inputStream);
        return msg;
    }

    public long addTask(Task.Type type, long a, long b, long p, long m, long n) {
        return taskList.addTask(type, a, b, p, m, n);
    }

    public long subscribeOnTaskResult(long taskId) {
        return taskList.subscribeOnTaskResult(taskId);
    }

    public List<Task> getTaskList() {
        return taskList.getTasksList();
    }
}