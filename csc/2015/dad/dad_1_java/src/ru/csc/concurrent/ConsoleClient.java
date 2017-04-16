package ru.csc.concurrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ConsoleClient {
    public static void main(String[] args) throws InterruptedException, UnsupportedOperationException, IOException {
        int threadsCount = Integer.parseInt(args[0]);
        MyLittleThreadPool<Void> pool = new MyLittleThreadPool<>(threadsCount);
        System.out.println("Usage:\n add N returns taskID\n cancel taskID \n status taskID");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String[] query = reader.readLine().split(" ");
            switch (query[0]) {
                case "add":
                    int n = Integer.parseInt(query[1]);
                    int taskId = pool.submit(LittleTask.createTimedTask(n));
                    System.out.println("TaskID " + taskId);
                    break;
                case "cancel":
                    int taskID = Integer.parseInt(query[1]);
                    LittleTask<Void> task = pool.getTask(taskID);
                    boolean status = task.cancel(true);
                    if (status) {
                        System.out.println("Task has been cancelled");
                    } else {
                        System.out.println("Task can't be cancelled");
                    }
                    break;
                case "status":
                    taskID = Integer.parseInt(query[1]);
                    task = pool.getTask(taskID);
                    System.out.println(task.status());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }
}