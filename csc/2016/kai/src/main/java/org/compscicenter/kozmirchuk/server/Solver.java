package org.compscicenter.kozmirchuk.server;

import java.util.*;
import java.util.stream.Collectors;


public class Solver {

    private final HashMap<Integer, TaskWrapper> tasks = new HashMap<>();
    private final Queue<TaskWrapper> readyTasks = new ArrayDeque<>();

    public Solver(int maxThreads) {

        for (int i = 0; i < maxThreads; ++i) {
            new Thread(new Worker(readyTasks, tasks), "Worker").start();
        }

    }


    public boolean push(TaskWrapper task) {

        final List<Protocol.Task.Param> params = new ArrayList<>();
        params.add(task.task.getA());
        params.add(task.task.getB());
        params.add(task.task.getP());
        params.add(task.task.getM());


        final List<TaskWrapper> parentValueLocks = new ArrayList<>();
        synchronized (tasks) {

            for (Protocol.Task.Param value : params) {
                if (!value.hasValue()) {
                    TaskWrapper parentTaskId = tasks.get((int) value.getDependentTaskId());
                    if (parentTaskId == null) return false;
                    parentValueLocks.add(parentTaskId);

                }
            }

            tasks.put(task.taskId, task);
        }

        new Thread(() -> {

            for (TaskWrapper parentTask : parentValueLocks) {
                try {
                    synchronized (parentTask.readyLock) {
                        while (!parentTask.done.get())
                            parentTask.readyLock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (readyTasks) {
                readyTasks.add(task);
                readyTasks.notify();
            }

        }, "Dependency Resolver").start();

        return true;

    }


    public List<TaskWrapper> getFinishedTasks() {
        synchronized (tasks) {
            return tasks.entrySet().stream().map(Map.Entry::getValue).filter(TaskWrapper::isDone).collect(Collectors.toList());
        }
    }

    public TaskWrapper getTask(int id) {
        synchronized (tasks) {
            return tasks.get(id);
        }
    }


    private static class Worker implements Runnable {

        private final Queue<TaskWrapper> queue;
        private final HashMap<Integer, TaskWrapper> tasks;

        public Worker(Queue<TaskWrapper> queue, HashMap<Integer, TaskWrapper> tasks) {
            this.queue = queue;
            this.tasks = tasks;
        }

        @Override
        public void run() {

            while (true) {
                try {
                    TaskWrapper taskWrapper;
                    synchronized (queue) {
                        while (queue.isEmpty()) {
                            queue.wait();
                        }
                        taskWrapper = queue.poll();
                    }

                    Protocol.Task task = taskWrapper.task;

                    synchronized (tasks) {
                        long a = task.getA().hasValue() ? task.getA().getValue() : tasks.get((int) task.getA().getDependentTaskId()).getResult();
                        long b = task.getB().hasValue() ? task.getB().getValue() : tasks.get((int) task.getB().getDependentTaskId()).getResult();
                        long p = task.getP().hasValue() ? task.getP().getValue() : tasks.get((int) task.getP().getDependentTaskId()).getResult();
                        long m = task.getM().hasValue() ? task.getM().getValue() : tasks.get((int) task.getM().getDependentTaskId()).getResult();
                        long n = task.getN();
                        taskWrapper.run(a, b, p, m, n);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
