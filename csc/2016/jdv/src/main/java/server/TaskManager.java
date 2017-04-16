package server;
import communication.Protocol;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TaskManager {

    private AtomicInteger taskCount;

    private TaskSolver taskSolver;

    private TaskStore taskStore;

    public TaskManager() {
        taskCount = new AtomicInteger();
        taskStore = new TaskStore();
        taskSolver = new TaskSolver(taskStore);

    }

    public int addTask(String clientId, Protocol.Task task) throws InterruptedException {
        int taskId = taskCount.addAndGet(1);
        ClientTask clientTask = new ClientTask(clientId, taskId, task);
        taskStore.addTask(taskId, clientTask);
        taskSolver.solveTask(clientTask);
        return taskId;
    }

    public LinkedList<Protocol.ListTasksResponse.TaskDescription> getTasks() {
        LinkedList<Protocol.ListTasksResponse.TaskDescription> listTasks = new LinkedList<>();
        for (Map.Entry<Integer, ClientTask> taskInfo : taskStore.entrySet()) {
            ClientTask task = taskInfo.getValue();
            Protocol.ListTasksResponse.TaskDescription.Builder descDuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder();

            descDuilder.setTask(task.getTask());
            descDuilder.setTaskId(taskInfo.getKey());
            if (task.isSolved())
                descDuilder.setResult(task.getResult());
            descDuilder.setClientId(task.getClientId());
            listTasks.add(descDuilder.build());
        }
        return listTasks;
    }

    public long getResult(int taskId) throws Exception {
        if (!taskStore.contains(taskId))
            throw new Exception();
        return taskStore.getResult(taskId);
    }
}
