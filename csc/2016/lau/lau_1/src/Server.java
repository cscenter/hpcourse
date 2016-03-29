import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Server {
    private TaskList taskList;

    Server() {
        taskList = new TaskList();
    }

    void addTask(Task task) {
        if (task.type == Task.Type.INDEPENDENT) {
            taskList.addIndependentTask(task);
        } else {
            taskList.addDependentTask(task);
        }
    }

    void subscribeOnTaskResult(long taskId) {

    }

    List<Task> getRunningTaskList() {
        return null;
    }
}


class Task {
    enum Type {
        DEPENDENT,
        INDEPENDENT
    }

    enum Status {
        RUNNING,
        FINISHED
    }

    long id;
    long a, b, c, d;
    long n;
    long result;

    Type type;
    Status status;

    Task(long id, Type type, long a, long b, long c, long n) {
        this.type = type;
        this.a = a;
        this.b = b;
        this.c = c;
        this.n = n;
        this.id = id;
        status = Status.RUNNING;
    }

    Task(long id, Type type, long ... args) {
        this.type = type;
        this.a = args[0];
        this.b = args[1];
        this.c = args[2];
        this.n = args[3];
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

    TaskList() {
        root = new Node();
        end = new Node();
        root.next = end;
    }

    void addIndependentTask(Task task) {
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

    void addDependentTask(Task task) {
        List<Long> dependentTaskIds = Arrays.asList(task.a, task.b, task.c);
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
        }
        currentNode.isLocked = false;
    }
}