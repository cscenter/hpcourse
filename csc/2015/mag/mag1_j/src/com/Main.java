package com.cscenter;

import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    static void displayHelp(){
        System.out.println("Supported commands:");
        System.out.println("Task N - create sleeping task with duration N");
        System.out.println("Status N - get status of task N");
        System.out.println("Kill N - kill task N");
        System.out.println("Exit - exit program");
    }

    public static void main(String[] args) {
        int Arg = 10;
        if (args.length > 0) {
            try {
                Arg = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Argument" + args[0] + " must be an integer.");
                System.exit(1);
            }
        }else{
            System.err.println("Provide number of threads: a numeric argument");
            System.exit(1);
        }
        ThreadPoolManager poolManager = new ThreadPoolManager(Arg);
        System.out.println("Started threadpool with " + args[0] + "arguments");
        boolean f = true;
        int num = 1;
        String s;
        int n;
        ArrayList<FutureTask<?>> tasks = new ArrayList<FutureTask<?>>();
        while(f){
            Scanner inp = new Scanner(System.in);
            String command = inp.next().toLowerCase();
            switch (command){
                case "task":
                    s = inp.next().toLowerCase();
                    n = -1;
                    try {
                        n = Integer.parseInt(s);
                    }catch (NumberFormatException e){
                        System.out.println("Invalid number " + s);
                        displayHelp();
                    }
                    tasks.add(poolManager.submit(new SleepingTask(Integer.toString(num), n), null));
                    num ++;
                    break;
                case "status":
                    s = inp.next().toLowerCase();
                    n = -1;
                    try {
                        n = Integer.parseInt(s);
                    }catch (NumberFormatException e){
                        System.out.println("Invalid number " + s);
                        displayHelp();
                    }
                    if(tasks.get(n - 1).isDone()){
                        System.out.println("Status of task " + s + " is DONE");
                    }else if(tasks.get(n - 1).isCancelled()){
                        System.out.println("Status of task " + s + " is CANCELLED");
                    }else{
                        System.out.println("Status of task " + s + " is RUNNING");
                    }
                    break;
                case "kill":
                    s = inp.next().toLowerCase();
                    n = -1;
                    try {
                        n = Integer.parseInt(s);
                    }catch (NumberFormatException e){
                        System.out.println("Invalid number " + s);
                        displayHelp();
                    }
                    tasks.get(n - 1).cancel(true);
                    System.out.print("Cancelling task " + s);
                    break;
                case "exit":
                    System.out.println("Exiting");
                    f = false;
                    break;
                default:
                    System.out.print("Unrecognized command " + command);
                    displayHelp();
            }
        }

        poolManager.shutdown();
    }

}
