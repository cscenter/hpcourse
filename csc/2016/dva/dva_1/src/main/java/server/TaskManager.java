package server;

import communication.Protocol;
import task.TaskParam;
import task.TaskThread;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class TaskManager {
    // TODO remove finished tasks from tasks map
    private final Map<Integer, TaskThread> tasks = new HashMap<>();
    private final Map<Integer, Long> results = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * @param task
     * @return id assigned to task
     */
    int addTask(Protocol.Task task) {
        final int id = counter.incrementAndGet();
        TaskParam a, b, p, m;
        long n;
        a = buildTaskParam(task.getA());
        b = buildTaskParam(task.getB());
        p = buildTaskParam(task.getP());
        m = buildTaskParam(task.getM());
        n = task.getN();

        final TaskThread t = new TaskThread(id, a, b, p, m, n);
        t.start();

        synchronized (tasks) {
            tasks.put(id, t);
        }
        new Thread(() -> getResult(id)).start();
        return id;
    }

    Protocol.Task getTask(int id) {
        TaskThread t = getTaskThread(id);

        Protocol.Task.Builder builder = Protocol.Task.newBuilder();
        builder.setA(buildProtocolTaskParam(t.a))
                .setB(buildProtocolTaskParam(t.b))
                .setM(buildProtocolTaskParam(t.m))
                .setP(buildProtocolTaskParam(t.p))
                .setN(t.n);
        return builder.build();

    }

    private TaskThread getTaskThread(int id) throws IllegalArgumentException {
        TaskThread t = tasks.get(id);
        if (t == null) {
            throw new IllegalArgumentException("Task not found, id = " + id);
        }
        return t;
    }

    // Gets result of task and adds tasks to
    long getResult(int taskId) {
        Long result;
        synchronized (results) {
            result = results.get(taskId);
            if (result != null)
                return result;
        }

        TaskThread t = getTaskThread(taskId);
        result = t.getResult();
        synchronized (results) {
            // There is a chance that put() for the same taskId
            // wil be called from multiple threads, but it's not a problem
            results.put(taskId, result);
        }

        return result;
    }

    /**
     * @return all active tasks
     */
    Collection<Integer> getRunningTasks() {
        synchronized (tasks) {
            synchronized (results) {
                return tasks.keySet()
                        .stream()
                        .filter(x -> !results.containsKey(x))
                        .collect(Collectors.toList());
            }
        }
    }

    private task.TaskParam buildTaskParam(Protocol.Task.Param param) {
        if (param.hasValue()) {
            return new TaskParam(param.getValue());
        } else {
            return new TaskParam(getTaskThread(param.getDependentTaskId()));
        }
    }

    private Protocol.Task.Param buildProtocolTaskParam(task.TaskParam taskParam) {
        Protocol.Task.Param.Builder builder = Protocol.Task.Param.newBuilder();
        Integer id = taskParam.getTaskId();
        if (id != null) {
            builder.setDependentTaskId(id);
        } else {
            builder.setValue(taskParam.getValue());
        }
        return builder.build();
    }
}
