package Server;

import communication.Protocol;
import communication.Protocol.ListTasksResponse.TaskDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by scorpion on 15.04.16.
 */
public class Helper {

    private Map< Integer, TaskDescription> tasks;

    private Helper() {
        tasks = Collections.synchronizedMap(new HashMap<Integer, TaskDescription>());
    }

    public static Helper getInstance() {
        return Singelton.instance;
    }

    private static class Singelton {
        public static Helper instance = new Helper();
    }

    public void addTask(int id, TaskDescription td) {
        tasks.put(id, td);
    }

    public TaskDescription getTaskById(int id) {
            return tasks.get(id);
    }

    ArrayList<TaskDescription> getAllTask() {
        synchronized (tasks) {
            ArrayList<TaskDescription> result = new ArrayList<>();
            for (TaskDescription td : tasks.values()) {
                result.add(td);
            }
            return result;
        }
    }
}
