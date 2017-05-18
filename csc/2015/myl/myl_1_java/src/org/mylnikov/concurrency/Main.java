package org.mylnikov.concurrency;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by alex on 5/13/2015.
 */
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("tread num");
        boolean flag = true;
        String input = in.nextLine();
        int n = Integer.parseInt(input);

        System.out.println("Help commands:");
        System.out.println("add time(s)(a 10)\n" +
                " c id (c1) Cancel Interrupt\n s id(s 1) Status\n e Exit");
        MylPool pool = new MylPool(n);
        while (flag) {
            try {
                System.out.println("command: ");
                input = in.nextLine().split("\\n")[0];
                if (input.equals("e")) {
                    flag = false;
                    continue;
                } else {
                    if (input.startsWith("s ")) {
                        long id = Long.parseLong(input.substring("s ".length()));
                        System.out.println(pool.getStatus(id));
                    } else if (input.startsWith("a ")) {
                        int duration = Integer.parseInt(input.substring("a ".length()));
                        MylTask task = new MylTask(duration);
                        System.out.println("id= " + task.getId() + " add");
                        pool.submit(Executors.callable(task), task.getId());
                    } else if (input.startsWith("c ")) {
                        long id = Long.parseLong(input.substring("c ".length()));

                        if (!pool.cancel(id)) {
                            System.out.println("id = " + id + "not canceled Possibly exeption happaned ");
                        } else {
                            System.out.println("id = " + id + " canceled");
                        }

                    } else
                        System.out.println("enter valid command");
                }
            } catch (Exception e) {
                System.out.println("some par error");
            }
        }
        pool.shutdown();
    }
}
