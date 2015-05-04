import java.io.*;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class Main {

    public static void main(String[] args) {
        try {

            Dictionary<Integer, ConcurrentScheduler.Future> tasks = new Hashtable<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(System.out), true);


            pw.println("possible commands:");
            pw.println("add N, where N - integer amount of seconds of task");
            pw.println("cancell ID, where ID is integer number of task");
            pw.println("status ID, where ID is integer number of task");
            pw.println("exit, to ShutDown Scheduler");

            pw.println("Please enter number of threads");

            int threads = Integer.decode(br.readLine());

            ConcurrentScheduler scheduler = new ConcurrentScheduler(threads);
            pw.println("Scheduler started");

            int tasksCount = 0;
            boolean notExit = true;
            while(notExit) {
                pw.println("Please enter command");
                String command = br.readLine();
                if (command.equals("exit")) {
                    scheduler.shutDown();
                    notExit = false;
                }
                else if (command.contains("add")) {
                    int n = Integer.decode(command.replace("add ", ""));
                    int taskNumber = ++tasksCount;
                    pw.println("adding task with ID " + Integer.toString(taskNumber));

                    try {
                        tasks.put(taskNumber, scheduler.addTask(new Task(n, Integer.toString(taskNumber)), taskNumber));
                    }
                    catch (Exception e) {
                        pw.println(e.getMessage());
                    }
                }
                else if (command.contains("status")) {
                    int n = Integer.decode(command.replace("status ",""));
                    pw.println(tasks.get(n).getStatus());
                }
                else if (command.contains("cancell")) {
                    int n = Integer.decode(command.replace("cancell ",""));
                    tasks.get(n).cancell();
                }
            }
        } catch (IOException e) {

        }
    }


}
