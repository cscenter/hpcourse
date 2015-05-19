package ru.csc.concurrent;

import java.util.Scanner;

public class Main {
    private final static int COUNT_THREADS = 3;

    public static void main(String[] args) {
        System.out.println("Usage: \n add -- adds n-second task \n " +
                "cancel -- end task with taskID \n " +
                "status -- return status for task with taskID");

        ThreadPool threadPool = new ThreadPool(COUNT_THREADS);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String command = scanner.next();
            switch (command) {
                case "add":
                    int n = scanner.nextInt();
                    int id = threadPool.submit(ThreadPoolTask.createTimedTask(n));
                    System.out.println("Task id: " + id);
                    break;
                case "cancel":
                    int taskId = scanner.nextInt();
                    threadPool.interrupt(taskId);
                    break;
                case "status":
                    int taskIdForStatus = scanner.nextInt();
                    ThreadPoolTask<?> task = threadPool.getTaskById(taskIdForStatus);
                    System.out.println(renderTaskStatus(task.getStatus()));
                    break;
                default:
                    break;
            }
        }
    }

    static String renderTaskStatus(ThreadPoolTask.Status status) {
        switch (status) {
            case CANCELLED:
                return "Task is cancelled";
            case DONE:
                return "Task is done";
            case RUNNING:
                return "Task is running";
            case EXECUTION_EXCEPTION:
                return "Task was interrupted by exception";
            case WAITING:
                return "Task is waiting for execution";
        }

        return null;
    }
}
