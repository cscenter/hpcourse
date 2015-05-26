import ru.compscicenter.ThreadPool.*;
import ru.compscicenter.ThreadPool.TestTasks.CountingTask;
import ru.compscicenter.ThreadPool.TestTasks.ExceptionTask;
import ru.compscicenter.ThreadPool.TestTasks.RecursiveTask;
import ru.compscicenter.ThreadPool.TestTasks.SleepingTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {
        Map<Integer, TaskFuture> tasks = new HashMap<>();

        if(args.length < 1) {
            throw new Exception("Not enough arguments. Amount of working threads is required.");
        }
        int taskId = 0;
        Integer threadsAmount = Integer.valueOf(args[0]);
        FixedThreadPool ftp = new FixedThreadPool(threadsAmount);

        Scanner in = new Scanner(System.in);
        while (true) {
            String command = in.next();
            if(command.equals("stop")) {
                System.out.println("stopping threads");
                ftp.stop();
                break;
            }
            else if(command.equals("add")) {
                String taskName = in.next();
                Task task;
                if(taskName.equals("sleeping")) {
                    int delay = in.nextInt();
                    task = new SleepingTask(delay);
                }
                else if(taskName.equals("counting")) {
                    task = new CountingTask();
                }
                else if(taskName.equals("throwing")) {
                    task = new ExceptionTask();
                }
                else if(taskName.equals("recursive")) {
                    int delay = in.nextInt();
                    task = new RecursiveTask(ftp, delay);
                }
                else {
                    System.out.println("Unknown task type");
                    continue;
                }
                System.out.println("task №" + taskId + " is added to queue");
                tasks.put(taskId++, ftp.submit(task));
                
            }
            else if(command.equals("cancel")) {
                int id = in.nextInt();
                TaskFuture task = tasks.get(id);
                if(task == null) {
                    System.out.println("Task not found");
                    return;
                }
                task.cancel(true);
            }
            else if(command.equals("result")) {
                int id = in.nextInt();
                TaskFuture task = tasks.get(id);
                if(task == null) {
                    System.out.println("Task not found");
                    return;
                }
                Object result = task.get();
                System.out.println(result);
            }
            else if(command.equals("iscanceled")) {
                int id = in.nextInt();
                TaskFuture task = tasks.get(id);
                if(task == null) {
                    System.out.println("Task not found");
                    return;
                }
                System.out.println(task.isCancelled());
            }
            else if(command.equals("exception")) {
                int id = in.nextInt();
                TaskFuture task = tasks.get(id);
                if(task == null) {
                    System.out.println("Task not found");
                    return;
                }
                System.out.println(task.getException().toString());
            }
            else {
                System.out.println("Unknown command: " + command);
            }
        }
    }
}
