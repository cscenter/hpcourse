package server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class TaskList {
    private class Node {
        AtomicBoolean isLocked = new AtomicBoolean(false);
        Task task;
        Node next;
    }

    private Node root, end;
    private AtomicInteger currentTaskId;

    TaskList() {
        root = new Node();
        end = new Node();
        root.next = end;
        currentTaskId = new AtomicInteger(0);
    }

    int addTask(Task.Type type, String clientId, long a, long b, long p, long m, long n) {
        int taskId;
        taskId = currentTaskId.getAndAdd(1);
        Task task = new Task(taskId, type, clientId, a, b, p, m, n);
        System.out.println("TaskList: submitting task " + task.toString());

        if (task.type == Task.Type.INDEPENDENT) {
            addIndependentTask(task);
        } else {
            addDependentTask(task);
        }
        return taskId;
    }

    long subscribeOnTaskResult(long taskId) {
        while (true) {
            Node currentNode = root.next;
            while (currentNode != end) {
                synchronized (currentNode) {
                    Task task = currentNode.task;
                    if (task.id == taskId) {
                        try {
                            while (task.status != Task.Status.FINISHED) {
                                currentNode.wait();
                            }
                            return task.result;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    currentNode = currentNode.next;
                }
            }
        }
    }

    List<Task> getTasksList() {
        List<Task> result = new ArrayList<>();
        Node currentNode = root.next;
        Task task;
        while (currentNode != end) {
            synchronized (currentNode) {
                task = currentNode.task;
                currentNode = currentNode.next;
            }
            result.add(task);
        }
        return result;
    }

    void addIndependentTask(Task task) {
        Node newNode = new Node();
        newNode.task = task;
        Node currentNode = root;

        while (currentNode.next != end) {
            if (!currentNode.isLocked.get()) {
                insertNode(currentNode, newNode);
                return;
            }
            currentNode = currentNode.next;
        }
        insertNode(currentNode, newNode);
    }

    void addDependentTask(Task task) {
        List<Long> dependentTaskIds = Arrays.asList(task.a, task.b, task.p, task.m);
        long[] dependentTasksResults = new long[dependentTaskIds.size() + 1];
        dependentTasksResults[dependentTasksResults.length - 1] = task.n;

        for (int i = 0; i < dependentTaskIds.size(); i++) {
            dependentTasksResults[i] = subscribeOnTaskResult(dependentTaskIds.get(i));
        }

        task.valueA = dependentTasksResults[0];
        task.valueB = dependentTasksResults[1];
        task.valueP = dependentTasksResults[2];
        task.valueM = dependentTasksResults[3];
        task.n = dependentTasksResults[4]; // Redundant?
        addIndependentTask(task);
    }

    void insertNode(Node currentNode, Node newNode) {
        currentNode.isLocked.set(true);
        synchronized (currentNode) {
            newNode.next = currentNode.next;
            currentNode.next = newNode;
        }
        currentNode.isLocked.set(false);
        startTask(newNode);
    }

    private void startTask(Node node) {
        new Thread(() -> {
            Task task = node.task;
            long n = task.n;
            long a = task.valueA;
            long b = task.valueB;
            long p = task.valueP;
            long m = task.valueM;
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            task.result = a;
            synchronized (node) {
                task.status = Task.Status.FINISHED;
                node.notifyAll();
            }
        }).start();
    }
}
