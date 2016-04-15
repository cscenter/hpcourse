package server;

import utils.Task;
import utils.TaskParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/*  Класс-синглтон для хранения информации о запущенных задачах */
public class Controller {

    private AtomicInteger idCounter;
    private Map <Integer, Task> taskMap;

    // Singleton
    private Controller(){
        idCounter = new AtomicInteger(0);
        taskMap = Collections.synchronizedMap(new HashMap<Integer, Task>());
    }

    public static Controller getInstance(){
        return SingletonHolder.instance;
    }

    private static class SingletonHolder {
        public static Controller instance = new Controller();
    }

    public int idIncrement(){
        return idCounter.incrementAndGet();
    }

    public ArrayList<Task> getTaskList(){
        return new ArrayList<>(taskMap.values());
    }

    public Task getTaskById (int id){
        return Controller.getInstance().taskMap.get(id);
    }

    // добавление и запуск новой задачи
    public int addTask(TaskParameter a, TaskParameter b, TaskParameter p, TaskParameter m, TaskParameter n, String clientId){
        int currentId = idIncrement();
        Task newTask = new Task(a, b, p, m, n, currentId, clientId);
        taskMap.put(currentId, newTask);
        new Thread(newTask).start();
        return currentId;
    }


}
