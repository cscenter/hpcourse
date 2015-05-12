package com.den.concurrency;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * @author Zaycev Denis
 */
public class Main {

    private static final String EXIT_COMMAND   = "exit";
    private static final String ADD_COMMAND    = "add";
    private static final String CANCEL_COMMAND = "cancel";
    private static final String STATE_COMMAND  = "state";

    private static ThreadPool pool;

    private static Map<Integer, Task> tasks = new HashMap<Integer, Task>();

    private static int nextTaskId = 1;

    public static void main(String[] args) {
        // Yep! I know that it's a bad way
        // to parse arguments like in following
        // logic but it's the easiest way for now.

        if (args.length < 1 || args.length > 2) {
            throw new IllegalStateException(
                    "Wrong arguments count. Should be one or two: <threads_count> [<run_in_test_mode>]");
        }

        pool = new ThreadPool(getInteger(args[0]));
        startProcessing(args);
        pool.shutDown();
    }

    private static void startProcessing(String[] args) {
        if (args.length == 2 && Boolean.parseBoolean(args[1])) {
            startTestProcessing();
        } else {
            startCommonProcessing();
        }
    }

    private static void startCommonProcessing() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if (EXIT_COMMAND.equals(line)) {
                return;

            } else {

                try {
                    doTheJob(line.split("\\s"));
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }

            }
        }
    }

    private static void startTestProcessing() {
        try {

            System.out.println("Command execution:");

            for (int i = 1; i <= 10; i++) {
                doTheJob(new String[] { ADD_COMMAND, String.valueOf(i) });
            }

            for (int i = 2; i <= 10; i += 2) {
                try {
                    doTheJob(new String[] { CANCEL_COMMAND, String.valueOf(i) });
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }

            System.out.println("Sleeping...");
            Thread.sleep(15000);

            System.out.println("Tasks states: ");
            for (int i = 1; i <= 10; i++) {
                System.out.println(i + ": " + tasks.get(i).getState());
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static void doTheJob(final String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Wrong arguments count. Should be two.");
        }

        String message;
        if (CANCEL_COMMAND.equals(args[0])) {
            message = doCancel(getInteger(args[1]));

        } else if (ADD_COMMAND.equals(args[0])) {
            message = doAdd(getInteger(args[1]));

        } else if (STATE_COMMAND.equals(args[0])) {
            Task task = tasks.get(getInteger(args[1]));
            message = task == null ? "no such task" : task.getState().toString();

        } else {
            throw new IllegalArgumentException("Unknown command");
        }

        if (message != null) {
            System.out.println(message);
        }
    }

    private static String doCancel(Integer taskId) {
        Task task = tasks.get(taskId);
        if (task != null) {
            try {

                task.terminate();

            } catch (Exception e) {
                return e.getMessage();
            }
        }

        return task == null ? "no such task" : null;
    }

    private static String doAdd(final Integer timingInS) {
        Task<Long> task = new Task<Long>(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                Long sleepMs = timingInS * 1000L;
                Thread.sleep(sleepMs);

                System.out.println(sleepMs);

                return sleepMs;
            }
        });

        Integer taskId = nextTaskId++;
        tasks.put(taskId, task);

        pool.submit(task);

        return taskId.toString();
    }

    private static Integer getInteger(String numStr) {
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number expected, but got " + numStr);
        }
    }

}
