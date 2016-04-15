package server;
import communication.Protocol;
import communication.Protocol.Task;
import server.TaskCalculator;
import server.TaskRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TaskManager {
    private TaskRepository taskRep;
    private TaskCalculator taskCalc;
    private AtomicInteger taskIdCounter;

    TaskManager() {
        taskIdCounter = new AtomicInteger(0);
        taskRep = TaskRepository.getInstance();
        taskCalc = new TaskCalculator();
    }

    public int submitTask(String ClientID, Protocol.Task task) {
        int id = taskIdCounter.getAndIncrement();
        server.Task newTask = new server.Task(task, id, ClientID);
        taskRep.putTask(id, newTask);
        taskCalc.solve(newTask);

        return id;
    }

    public Long getResult(int taskId) {
        return taskRep.getTaskById(taskId).getResult();
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getTasks() {
        return taskRep.getTasksList();
    }



}
