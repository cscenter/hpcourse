package ru.csc2015.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;

public class Main {
    private static final String SUBMIT = "submit";
    private static final String CANCEL = "cancel";
    private static final String STATUS = "status";
    private static final String EXIT = "exit";

    private static void printUsage() {
        System.out.println("Command line usage: ");
        System.out.println("1) Submit - add a task");
        System.out.println("2) Cancel - cancel task with certain id");
        System.out.println("3) Status - check task status");
        System.out.println("4) Exit - exit the application");
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        int numOfThreads;

        if (args.length != 1) {
            System.out.println("Wrong input parameter, must be integer - the number of threads in the pool");
            return;
        } else {
            numOfThreads = Integer.parseInt(args[0]);

            if (numOfThreads <= 0) {
                System.out.println("Number of threads must be positive");
                return;
            }
        }

        printUsage();

        boolean runFlag = true;
        FixedThreadPool threadPool = new FixedThreadPool(numOfThreads);
        Map<Long, MyFuture<?>> futureMap = new HashMap<>();
        threadPool.start();

        while (runFlag) {
            String command = scanner.next().toLowerCase();

            switch (command) {
                case EXIT:
                    threadPool.exit();
                    runFlag = false;
                    break;
                case SUBMIT: {
                    Long duration = Long.parseLong(scanner.next());
                    Task task = new Task(duration);
                    MyFuture<?> future = threadPool.submit(Executors.callable(task), task.getId());
                    futureMap.put(task.getId(), future);
                    System.out.println("Add task with id: " + task.getId());
                    break;
                }
                case CANCEL: {
                    Long id = Long.parseLong(scanner.next());
                    futureMap.get(id).cancel(true);
                    break;
                }
                case STATUS: {
                    Long id = Long.parseLong(scanner.next());
                    System.out.println(futureMap.get(id).getStatus());
                    break;
                }
                default:
                    System.out.println("Wrong command");
            }
        }
    }
}
