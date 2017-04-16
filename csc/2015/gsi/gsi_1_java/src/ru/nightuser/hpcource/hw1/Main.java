package ru.nightuser.hpcource.hw1;

import ru.nightuser.hpcource.hw1.examples.SimpleExampleTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {
  private static void usage() {
    System.out.println("Usage: COMMAND NUM_WORKERS");
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      usage();
      return;
    }

    int n;
    try {
      n = Integer.valueOf(args[0]);
    } catch (NumberFormatException nfe) {
      usage();
      return;
    }

    System.out.printf("Starting thread pool with %d threads%n", n);
    MyPool myPool = new MyPool(n);

    Pattern exitPattern = Pattern.compile("exit(?<force> force|)");
    Pattern addPattern = Pattern.compile("add (?<time>[0-9]+)");
    Pattern cancelPattern = Pattern.compile("cancel (?<id>[0-9]+)(?<force> force|)");
    Pattern statusPattern = Pattern.compile("status (?<id>[0-9]+)");
    Matcher m;

    ArrayList<MyTask<Integer>> tasks = new ArrayList<>();
    int currentId = 0;

    try (
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
    ) {
      while (true) {
        System.out.print("> ");
        String command = br.readLine();

        if ((m = exitPattern.matcher(command)).matches()) {

          boolean force = m.group("force").equals(" force");
          if (force) {
            System.out.println("Force exiting...");
            myPool.shutdownNow();
          } else {
            System.out.println("Exiting...");
            myPool.shutdown();
          }

          return;

        } else if ((m = addPattern.matcher(command)).matches()) {

          int id = currentId++;
          int time = Integer.valueOf(m.group("time"));

          Callable<Integer> target = new MySimpleTaskTarget(id, time);

          System.out.printf("Adding task: id=%d, time=%d%n", id, time);
          MyTask<Integer> task = myPool.submit(target);
          tasks.add(task);

        } else if ((m = cancelPattern.matcher(command)).matches()) {

          int id = Integer.valueOf(m.group("id"));
          boolean force = m.group("force").equals(" force");

          if (force) {
            System.out.printf("Force stopping %d", id);
          } else {
            System.out.printf("Stopping %d", id);
          }
          boolean result = tasks.get(id).cancel(force);
          System.out.printf(" %b%n", result);

        } else if ((m = statusPattern.matcher(command)).matches()) {

          int id = Integer.valueOf(m.group("id"));

          String answer;
          int status = tasks.get(id).getStatus();
          switch (status) {
            case MyTask.CANCELLED:
              answer = "Cancelled";
              break;
            case MyTask.DONE:
              answer = "Done";
              break;
            case  MyTask.RUNNING:
              answer = "Running";
              break;
            case MyTask.NORMAL:
              answer = "Awaiting";
              break;
            default:
              answer = "Unknown";
          }

          System.out.printf("Status of task %d: %s%n", id, answer);

        } else {

          System.out.println("Unknown command!");

        }
      }
    } catch (IOException ioe) {
      ioe.printStackTrace();
    } finally {
      myPool.shutdownNow();
    }
  }
}
