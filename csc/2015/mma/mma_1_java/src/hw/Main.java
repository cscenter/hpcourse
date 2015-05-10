package hw;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

// command line API for hw.FixedThreadPool
public class Main {
    public static void main(String[] args)  {
        int nThreads = -1;
        if (args.length == 1) {
            try {
                nThreads = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
            }
        }
        if (nThreads > 0) {
            new Main().start(nThreads);
        } else {
            System.out.println("Usage: java hw.Main <nThreads>");
            System.out.println("<nThreads>      size of ThreadPool");
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("new <duration_secs>         new task with specified duration in seconds");
        System.out.println("stop <task_id>              stop the task");
        System.out.println("status <task_id>            status of the task");
    }

    private void start(int nThreads) {
        Scanner in = new Scanner(System.in);
        FixedThreadPool pool = new FixedThreadPool(nThreads);
        List<Future> tasks = new LinkedList<>();
        usage();
        while (true) {
            Command cmd = new Command(in.nextLine());
            switch (cmd.getType()) {
                case "new":
                    if (cmd.hasIntArg() && cmd.getIntArg() >= 0) {
                        int duration_secs = cmd.getIntArg();
                        int taskId = tasks.size();
                        Future task = new Future(() -> {
                            System.out.printf("Starting task id=%d for %d\n", taskId, duration_secs);
                            Thread.sleep(duration_secs * 1000);
                            System.out.printf("DONE task id=%d\n", taskId);
                            return Optional.empty();
                        });

                        tasks.add(task);
                        pool.submit(task);
                        continue;
                    }
                case "stop":
                    if (cmd.hasIntArg()) {
                        int index = cmd.getIntArg();
                        if (0 < index && index <= tasks.size()) {
                            tasks.get(cmd.getIntArg() - 1).cancel();
                        } else {
                            System.out.println("no such task");
                        }
                        continue;
                    }
                case "status":
                    if (cmd.hasIntArg()) {
                        int index = cmd.getIntArg();
                        if (0 < index && index <= tasks.size()) {
                            System.out.println(tasks.get(cmd.getIntArg() - 1).getStatus());
                        } else {
                            System.out.println("no such task");
                        }
                        continue;
                    }
            }
            usage();
        }
    }

    private class Command {
        private String type = "";
        private boolean hasIntArg;
        private int intArg;

        public Command(String input) {
            String[] parts = input.split(" ");
            if (parts.length > 0) {
                type = parts[0].toLowerCase();
            }
            if (parts.length == 2) {
                try {
                    intArg = Integer.parseInt(parts[1]);
                    hasIntArg = true;
                } catch (NumberFormatException e) {
                }
            }
        }

        public String getType() {
            return type;
        }

        public boolean hasIntArg() {
            return hasIntArg;
        }

        public int getIntArg() {
            return intArg;
        }
    }
}
