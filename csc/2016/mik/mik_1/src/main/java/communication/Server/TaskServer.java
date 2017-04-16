package communication.Server;

import communication.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class TaskServer implements Runnable {
    Thread runner;
    private int portNumber;
    private Map<Integer, Object> taskMonitors;
    private Map<Integer, Long> taskResults;
    private List<Protocol.ListTasksResponse.TaskDescription> taskDescriptionsList;
    private InetAddress address;

    private int lastTaskId;

    public TaskServer(int portNumber, InetAddress address) {
        this.portNumber = portNumber;
        this.address = address;
        taskMonitors = new HashMap<>();
        taskResults = new HashMap<>();
        taskDescriptionsList = new ArrayList<>();
        lastTaskId = 0;
        runner = new Thread(this);
        this.runner.start();
    }

    public void setTaskResult(int taskId, long result) {
        taskResults.put(taskId, result);
    }

    public Long getTaskResult(int id) {
        return taskResults.get(id);
    }

    public boolean isFinished(int id) {
        return taskResults.containsKey(id);
    }

    public Object getTaskMonitor(int id) {
        return taskMonitors.get(id);
    }

    public void addTaskDescription(Protocol.ListTasksResponse.TaskDescription taskDescription) {
        taskDescriptionsList.add(taskDescription);
    }

    public boolean containsTask(int taskId) {
        return taskMonitors.containsKey(taskId);
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getTaskDescriptionsList() {
        return taskDescriptionsList;
    }

    public synchronized int generateTaskId() {
        lastTaskId++;
        return lastTaskId;
    }

    public synchronized Object generateTaskMonitor(int taskId) {
        Object monitor = new Object();
        taskMonitors.put(taskId, monitor);
        return monitor;
    }

    @Override
    public void run() {
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(portNumber, 0, address);
            while (true) {
                Socket connectionSocket = serverSocket.accept();
                new Thread(new ServerQueriesHandler(connectionSocket, this)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
