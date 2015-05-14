package ru.csc.hpcourse2015.smr;

import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Main {

    private final static String ADD_COMMAND = "add";
    private final static String CANCEL_COMMAND = "cancel";
    private final static String STATUS_COMMAND = "status";
    private final static String EXIT_COMMAND = "exit";

    static Integer numberThreads;
    static MyFixedThreadPool threadPool;

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("add <N>         - add a task with duration <N> sec.");
        System.out.println("cancel <id>     - remove a task <id> from the execution.");
        System.out.println("status <id>     - get status of task <id>");
        System.out.println("exit            - stop program and exit.");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Wrong number of arguments. For working enter <numberThreads>.");
        } else {
            try {

                numberThreads = Integer.parseInt(args[0]);
                startWorking();

            } catch (NumberFormatException exc) {
                System.out.println("Invalid number format. Please enter correct <numberThreads>.");
            } catch (Exception exc) {
                System.out.println(exc.getMessage());
            }
        }
    }

    private static void startWorking() {

        threadPool = new MyFixedThreadPool(numberThreads);
        Scanner in = new Scanner(System.in);
        usage();
        boolean exit = false;
        while (!exit) {
            try {
                String command = in.next().toLowerCase();
                switch (command) {
                    case ADD_COMMAND:

                        Integer duration = Integer.parseInt(in.next());
                        Task task = new Task(duration);
                        threadPool.submit(Executors.callable(task), task.getId());
                        System.out.println("Add task with id: " + task.getId());
                        System.out.println("Success!");
                        break;
                    case CANCEL_COMMAND: {

                        Integer id = Integer.parseInt(in.next());
                        if (threadPool.cancel(id))
                            System.out.println("Task with id: " + id + " has been cancelled.");
                        else
                            System.out.println("Task with id: " + id + " can't be cancelled.");
                        break;
                    }
                    case STATUS_COMMAND: {

                        Integer id = Integer.parseInt(in.next());
                        System.out.println("Task's status with id: " + id + " " + threadPool.status(id));
                        break;
                    }
                    case EXIT_COMMAND: {

                        threadPool.exit();
                        exit = true;
                        System.out.println("Thanks for working!");
                        break;
                    }
                    default: {
                        System.out.println("Wrong command!");
                        usage();
                        break;
                    }
                }
            } catch (Exception exc) {
                System.out.println(exc.toString());
            }
        }
    }
}
