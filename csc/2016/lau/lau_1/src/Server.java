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
        Thread thread = new Thread(() -> {
            Task task = new Task(currentTaskId++, type, a, b, p, m, n);
            if (task.type == Task.Type.INDEPENDENT) {
                taskList.addIndependentTask(task);
            } else {
                taskList.addDependentTask(task);
            }
            System.out.println("Server: task vs id = " + currentTaskId + " added. Params: "
                    + a + " " + b + " " + p + " " + m + " " + n);
        });
        threads.add(thread);
        thread.start();
        return currentTaskId;
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
                result.add(currentNode.task.clone());
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
        int resultsCount = 0;
        Node currentNode = root;
        dependentTasksResults[dependentTasksResults.length - 1] = task.n;

        while (currentNode != end) {
            currentNode.isLocked = true;
            synchronized (currentNode) {
                if (dependentTaskIds.contains(currentNode.task.id)) {
                    try {
                        while (currentNode.task.status == Task.Status.RUNNING) {
                            wait();
                        }
                        dependentTasksResults[resultsCount++] = currentNode.task.result;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                currentNode = currentNode.next;
            }
            currentNode.isLocked = false;
        }
        assert dependentTaskIds.size() == resultsCount;
        task = new Task(task.id, Task.Type.INDEPENDENT, dependentTasksResults);
        addIndependentTask(task);
    }

    private void insertNode(Node currentNode, Node newNode) {
        currentNode.isLocked = true;
        synchronized (currentNode) {
            newNode.next = currentNode.next;
            currentNode.next = newNode;
            startTask(newNode.task);
        }
        currentNode.isLocked = false;
    }

    private void startTask(Task task) {
        new Thread(() -> {
            while (task.n-- > 0) {
                task.b = (task.a * task.p + task.b) % task.m;
                task.a = task.b;
            }
            task.result = task.a;
            task.status = Task.Status.FINISHED;
            synchronized (task) {
                task.notifyAll();
            }
        }).start();
    }
}