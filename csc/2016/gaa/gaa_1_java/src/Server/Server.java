package Server;


import communication.Protocol;
import communication.Protocol.ListTasksResponse.TaskDescription;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;
import util.SynchronizedInt;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by scorpion on 14.04.16.
 */

public class Server extends Thread{

    public Server(int serverPort) throws IOException {
        this.serverPort = serverPort;
        ids = new SynchronizedInt();
        serverSocket = new ServerSocket(serverPort);
    }

    Socket connection;

    @Override
    public void run() {
        try {
            while (true) {
                connection = serverSocket.accept();
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            ServerRequest request = ServerRequest.parseDelimitedFrom(connection.getInputStream());
                            if (request.hasSubmit()) {
                            }
                            if (request.hasSubscribe()) {
                            }
                            if (request.hasList()) {
                                new ListHandler(request).start();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        } catch (IOException e) {
            System.err.println("Error with serever socket!");
            e.printStackTrace();
        } finally {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getServerPort() {
        return serverPort;
    }

    private int serverPort;
    private ServerSocket serverSocket;
    private SynchronizedInt ids;
    private Helper helper;

    public class AbstractHandler extends Thread {

        ServerRequest request;

        AbstractHandler(ServerRequest request) {
            this.request = request;
        }

        public void sendMessage(ServerResponse message) throws IOException {
            synchronized (connection) {
                message.writeDelimitedTo(connection.getOutputStream());
            }
        }
    }

    public class ListHandler extends AbstractHandler {

        ListHandler(ServerRequest request) {
            super(request);
        }

        @Override
        public void run() {
            Protocol.ListTasksResponse.Builder builder = Protocol.ListTasksResponse.newBuilder().setStatus(Protocol.Status.OK);
            try {
                ArrayList<TaskDescription> tasksDescription = helper.getInstance().getAllTask();
                for (TaskDescription taskDescription : tasksDescription) {
                    builder.addTasks(taskDescription);
                }
            } catch (Exception e) {
                builder.setStatus(Protocol.Status.ERROR);
            }
            try {
                sendMessage((ServerResponse.newBuilder().setRequestId(request.getRequestId()).setListResponse(builder.build()).build()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
