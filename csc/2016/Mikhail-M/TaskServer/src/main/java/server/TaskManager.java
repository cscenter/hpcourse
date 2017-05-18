package server;
import communication.Protocol;
import server.TaskHelper.TaskCalculator;
import server.TaskHelper.TaskRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
        server.TaskHelper.Task newTask = new server.TaskHelper.Task(task, id, ClientID);
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
