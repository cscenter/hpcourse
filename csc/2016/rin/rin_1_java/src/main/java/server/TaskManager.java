package server;

import communication.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Ivan Rudakov
 */

public class TaskManager {

    private final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final AtomicInteger taskCounter = new AtomicInteger(0);

    private final Map<Integer, Task> taskMap = new Hashtable<>();

    public List<Protocol.ListTasksResponse.TaskDescription> getTasks() {
        List<Protocol.ListTasksResponse.TaskDescription> result = new ArrayList<>();

        for (Task task : taskMap.values()) {
            Protocol.ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder = Protocol.ListTasksResponse.TaskDescription.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setClientId(task.getClientId())
                    .setTask(task.getProtocolTask());

            if (task.isDone()) {
                taskDescriptionBuilder.setResult(task.getResult());
            }

            result.add(taskDescriptionBuilder.build());
        }
        return result;
    }

    public Integer submit(final String clientId, final Protocol.Task protocolTask) {
        Integer taskId = taskCounter.incrementAndGet();
        Task task = new Task(taskId, clientId, protocolTask);
        taskMap.put(taskId, task);
        task.start();
        return taskId;
    }

    public Long subscribe(final Integer taskId) {
        Task task = taskMap.get(taskId);
        while (!task.isDone()) {
            try {
                task.join();
            } catch (InterruptedException e) {
                log.error("Error while waiting task result", e);
            }
        }
        return task.getResult();
    }

    private class Task extends Thread {

        private final Integer taskId;

        private final String clientId;

        private final Protocol.Task protocolTask;

        private boolean done = false;

        private Long result;

        public Task(final Integer id, final String clientId, final Protocol.Task protocolTask) {
            this.taskId = id;
            this.clientId = clientId;
            this.protocolTask = protocolTask;
        }

        @Override
        public void run() {
            solve();
            done = true;
        }

        private void solve() {
            long a = getParam(protocolTask.getA());
            long b = getParam(protocolTask.getB());
            long p = getParam(protocolTask.getP());
            long m = getParam(protocolTask.getM());
            long n = protocolTask.getN();
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            result = a;
        }

        private long getParam(final Protocol.Task.Param param) {
            if (param.hasValue()) {
                return param.getValue();
            }

            int dependentTaskId = param.getDependentTaskId();
            Task task = taskMap.get(dependentTaskId);

            while (!task.isDone()) {
                try {
                    task.join();
                } catch (InterruptedException e) {
                    log.error("Error while waiting task result", e);
                }
            }

            return task.getResult();
        }

        public boolean isDone() {
            return done;
        }

        public Integer getTaskId() {
            return taskId;
        }

        public String getClientId() {
            return clientId;
        }

        public Protocol.Task getProtocolTask() {
            return protocolTask;
        }

        public Long getResult() {
            return result;
        }
    }
}
