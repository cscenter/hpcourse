import com.sun.org.apache.regexp.internal.RE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Server {
    private TaskList taskList;
    private long currentTaskId;
    private List<Thread> threads;

    Server() {
        taskList = new TaskList();
        threads = new ArrayList<>();
    }

    long addTask(Task.Type type, long a, long b, long p, long m, long n) {
        Task task = new Task(currentTaskId, type, a, b, p, m, n);

        Thread thread = new Thread(() -> {
            if (task.type == Task.Type.INDEPENDENT) {
                taskList.addIndependentTask(task);
            } else {
                taskList.addDependentTask(task);
            }
        });
        threads.add(thread);
        thread.start();
        return currentTaskId++;
    }

    void subscribeOnTaskResult(long taskId) {

    }

    List<Task> getTaskList() {
        return taskList.getTasksList();
    }
}


class Task implements Cloneable {
    @Override
    protected Task clone() {
        Task res = new Task(id, type, a, b, p, m, n);
        res.status = status;
        res.result = result;
        return res;
    }

    enum Type {
        DEPENDENT,
        INDEPENDENT
    }

    enum Status {
        RUNNING,
        FINISHED
    }

    long id;
    long a, b, p, m;
    long n;
    long result;

    Type type;
    Status status;

    Task(long id, Type type, long a, long b, long p, long m, long n) {
        this.type = type;
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.id = id;
        status = Status.RUNNING;
        System.out.println("Created task " + toString());
    }

    Task(long id, Type type, long ... args) {
        this.type = type;
        this.a = args[0];
        this.b = args[1];
        this.p = args[2];
        this.m = args[3];
        this.n = args[4];
        this.id = id;
        status = Status.RUNNING;
    }

    @Override
    public String toString() {
        return "Task id: " + id + " state: " + status.toString() + " type " + type.toString()
                + " params: " + a + " " + b + " " + p + " " + m + " " + n + " result: " + result;
    }
}

class TaskList {
    private class Node {
        Node next;
        boolean isLocked;
        Task task;
    }

    private Node root, end;

    public TaskList() {
        root = new Node();
        end = new Node();
        root.next = end;
    }

    public List<Task> getTasksList() {
        List<Task> result = new ArrayList<>();
        Node currentNode = root.next;
        while (currentNode != end) {
            synchronized (currentNode) {
                // TODO: here should be clone()
                result.add(currentNode.task);
                currentNode = currentNode.next;
            }
        }
        return result;
    }

    public void addIndependentTask(Task task) {
        Node newNode = new Node();
        newNode.task = task;
        Node currentNode = root;

        while (currentNode.next != end) {
            if (!currentNode.isLocked) {
                insertNode(currentNode, newNode);
                return;
            }
            currentNode = currentNode.next;
        }
        insertNode(currentNode, newNode);
    }

    public void addDependentTask(Task task) {
        List<Long> dependentTaskIds = Arrays.asList(task.a, task.b, task.p, task.m);
        long[] dependentTasksResults = new long[dependentTaskIds.size() + 1];
        dependentTasksResults[dependentTasksResults.length - 1] = task.n;

        for (int i = 0; i < dependentTaskIds.size(); i++) {
            long currentTaskId = dependentTaskIds.get(i);
            boolean isTaskFound = false;
            while (!isTaskFound) {
                Node currentNode;
                currentNode = root.next;
                while (currentNode != end) {
                    currentNode.isLocked = true;
                    synchronized (currentNode) {
                        if (currentNode.task.id == currentTaskId) {
                            try {
                                System.out.println("Going to wait for task id: " + currentNode.task.id);
                                while (currentNode.task.status != Task.Status.FINISHED) {
                                    currentNode.wait();
                                }
                                dependentTasksResults[i] = currentNode.task.result;
                                isTaskFound = true;
                                System.out.println("Finished dependency id: " + currentNode.task.id + " result " + dependentTasksResults[i]);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        currentNode.notifyAll();
                        currentNode = currentNode.next;
                    }
                    currentNode.isLocked = false;
                }
            }

            //throw new IllegalStateException("Could not find dependency id: " + currentTaskId + " in task list");
        }

        task.a = dependentTasksResults[0];
        task.b = dependentTasksResults[1];
        task.p = dependentTasksResults[2];
        task.m = dependentTasksResults[3];
        task.n = dependentTasksResults[4];
        addIndependentTask(task);
    }

    private void insertNode(Node currentNode, Node newNode) {
        currentNode.isLocked = true;
        synchronized (currentNode) {
            newNode.next = currentNode.next;
            currentNode.next = newNode;
        }
        startTask(newNode);
        currentNode.isLocked = false;
    }

    private void startTask(Node node) {
        new Thread(() -> {
            Task task = node.task;
            long n = task.n;
            long a = task.a;
            long b = task.b;
            long p = task.p;
            long m = task.m;
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            task.result = a;
            // TODO: here synchronization on task, but Node synch needed
            synchronized (node) {
                task.status = Task.Status.FINISHED;
                node.notifyAll();
            }
        }).start();
    }
}