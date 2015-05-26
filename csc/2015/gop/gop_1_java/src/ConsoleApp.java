import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Future;

public class ConsoleApp {
    private static final int DEFAULT_THRESHOLD = 20;
    private static final int QUEUE_LIMIT = 20;

    public static void main(String[] args) {
        if (args.length != 1) {
            usage();
        }

        int n = Runtime.getRuntime().availableProcessors();
        try {
            n = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            usage();
        }
        SimpleFixedThreadPool pool = new SimpleFixedThreadPool(n, QUEUE_LIMIT);

        System.out.println("Available commands:\nsleep int : sleeping task\nrecur int : recursive task\nstop int : stop task by ID\nstatus id : check status by id\nexit : shutdown now");
        int uniqueId = 1;
        Map<Integer, Future> tasks = new HashMap<>();
        Scanner s = new Scanner(System.in);
        for (;;) {
            String[] splitted = s.nextLine().split(" ");

            if (splitted[0].equals("exit")) {
                System.out.println("Shutting down the pool...");
                List<Runnable> unfinished = pool.shutdownNow();
                System.out.format("%d tasks unfinished", unfinished.size());
                System.exit(0);
            }

            if (splitted.length != 2) {
                System.out.println("Wrong command!");
                continue;
            }
            String cmd = splitted[0];
            int parameter;
            try {
                parameter = Integer.parseInt(splitted[1]);
            } catch (NumberFormatException nfe) {
                System.out.println("Wrong command!");
                continue;
            }

            switch (cmd) {
                case "sleep":
                    Future sf = pool.submit(new SleepingTask(parameter));
                    tasks.put(uniqueId, sf);
                    System.out.format("Submitted new sleeping task ID%d\n", uniqueId);
                    ++uniqueId;
                    break;
                case "recur":
                    Future<Integer> rf = pool.submit(new SimpleRecursiveTask(1, parameter, DEFAULT_THRESHOLD, pool));
                    tasks.put(uniqueId, rf);
                    System.out.format("Submitted new recursive task ID%d\n", uniqueId);
                    ++uniqueId;
                    break;
                case "stop":
                    Future toStop = tasks.get(parameter);
                    if (toStop == null) {
                        System.out.println("Wrong ID!");
                    }
                    else {
                        toStop.cancel(false);
                        System.out.format("Stopped task ID%d\n", parameter);
                    }
                    break;
                case "status":
                    Future toStatus = tasks.get(parameter);
                    if (toStatus == null) {
                        System.out.println("Wrong ID!");
                    }
                    else {
                        String formatString;
                        if(toStatus.isCancelled()) {
                            formatString = "Task ID%d is cancelled\n";
                        }
                        else {
                            formatString = toStatus.isDone() ? "Task ID%d is done\n" : "Task ID%d is running\n";
                        }
                        System.out.format(formatString, parameter);
                    }
                    break;
                default:
                    System.out.println("Wrong command!");
            }
        }
    }

    private static void usage() {
        System.out.println("Usage: [].jar numberOfThreads");
        System.exit(0);
    }
}
