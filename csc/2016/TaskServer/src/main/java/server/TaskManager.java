import communication.Protocol;
import communication.Protocol.Task;
import server.TaskCalculator;
import server.TaskRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TaskManager {
    private TaskRepository taskRep;
    private TaskCalculator taskCalc;
    private AtomicLong taskIdCounter;

    TaskManager() {
        taskIdCounter = new AtomicLong(0);
        taskRep = TaskRepository.getInstance();
        taskCalc = new TaskCalculator();
    }

    public long submitTask(String ClientID, Protocol.Task task) {
        long id = taskIdCounter.getAndIncrement();
        server.Task newTask = new server.Task(task, id, ClientID);
        taskRep.putTask(id, newTask);
        taskCalc.solve(newTask);

        return id;
    }


}
