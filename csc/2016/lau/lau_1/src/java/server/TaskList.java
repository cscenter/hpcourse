package server;

import java.util.ArrayList;
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

    int addTask(String clientId, TaskParam a, TaskParam b, TaskParam p, TaskParam m, long n) {
        int taskId;
        taskId = currentTaskId.getAndAdd(1);
        Task task = new Task(taskId, clientId, a, b, p, m, n);
        System.out.println("TaskList: submitting task " + task.toString());

        prepareTask(task);
        addTask(task);
        return taskId;
    }

    long subscribeOnTaskResult(int taskId) {
        while (true) {
            Node currentNode = root.next;
            while (currentNode != end) {
                Task task = currentNode.task;
                if (task.id == taskId) {
                    synchronized (currentNode) {
                        try {
                            while (task.status != Task.Status.FINISHED) {
                                currentNode.wait();
                            }
                            return task.result;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                currentNode = currentNode.next;
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

    void addTask(Task task) {
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

    void prepareTask(Task task) {
        for (int i = 0; i < task.params.length; i++) {
            if (task.params[i].type == TaskParam.Type.TASK_ID) {
                task.params[i].value = subscribeOnTaskResult(task.params[i].dependentTaskId);
            }
        }
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
            long a = task.params[0].value;
            long b = task.params[1].value;
            long p = task.params[2].value;
            long m = task.params[3].value;
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
