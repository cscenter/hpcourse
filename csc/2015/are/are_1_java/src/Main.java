import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * @author Ruslan Akhundov
 */
public class Main {

    private static void printUsage() {
        System.out.println("Type \"submit [DURATION]\" to add task with specified DURATION in milliseconds.");
        System.out.println("Type \"cancel [id]\" to cancel task with specified id.");
        System.out.println("Type \"status [id]\" to get status of task with specified id.");
        System.out.println("Type \"exit\" to exit");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Number of threads: ");
        Map<Integer, ThreadPoolImpl.FutureImpl> futureTasks = new HashMap<>();
        TaskFactory taskFactory = new TaskFactory();
        ThreadPoolImpl threadPool = new ThreadPoolImpl(Integer.parseInt(in.readLine()));
        threadPool.execute();
        printUsage();
        while (true) {
            String input = in.readLine();
            if ("exit".equals(input) || input == null) {
                threadPool.interrupt();
                return;
            } else if (input.startsWith("cancel ")) {
                Integer id = Integer.parseInt(input.substring("cancel ".length()));
                futureTasks.get(id).cancel(true);
            } else if (input.startsWith("submit ")) {
                long duration = Long.parseLong(input.substring("submit ".length()));
                TaskFactory.Task task = taskFactory.createTask(duration);
                System.out.println("Task " + task.getId() + " created.");
                ThreadPoolImpl.FutureImpl futureTask =
                        (ThreadPoolImpl.FutureImpl) threadPool.submit(Executors.callable(task), task.getId());
                futureTasks.put(task.getId(), futureTask);
            } else if (input.startsWith("status ")) {
                Integer id = Integer.parseInt(input.substring("status ".length()));
                System.out.println(futureTasks.get(id).getStatus());
            }
        }
    }
}
