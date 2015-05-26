package ConcurrentScheduler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Created by Anastasia on 04.05.2015.
 */
public class Main {
    public static void main(String[] args) {

        Dictionary<Integer, Future> FuturesCollection = new Hashtable<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Enter maximum number of threads");
        try {
            int MaxThreadsNumber = Integer.decode(reader.readLine());
            Scheduler MyScheduler = new Scheduler(MaxThreadsNumber);

            System.out.println("Now you can enter following commands:");
            System.out.println("1. submit N (where N is duration of task in seconds)");
            System.out.println("2. recursive N (where N is duration of task in seconds)");
            System.out.println("3. cancel ID (where ID is number of task)");
            System.out.println("4. state ID (where ID is number of task)");
            System.out.println("5. exit (to exit)");
            String command = reader.readLine();
            int CurrentTaskNumber = 1;

            while(!(command.equals("exit"))){
                String[] arguments = command.split(" ");
                if (arguments[0].equals("submit")){
                    Future future = MyScheduler.submit(new Task(CurrentTaskNumber, Integer.decode(arguments[1]) * 1000));
                    System.out.println("Task " + Integer.toString(CurrentTaskNumber) + " added");
                    FuturesCollection.put(CurrentTaskNumber++, future);
                }
                else if(arguments[0].equals("recursive")){
                    Future future = MyScheduler.submit(new RecursiveTask(CurrentTaskNumber, Integer.decode(arguments[1]) * 1000, MyScheduler));
                    System.out.println("Recursive task " + Integer.toString(CurrentTaskNumber) + " added");
                    FuturesCollection.put(CurrentTaskNumber++, future);
                }
                else if (arguments[0].equals("cancel") || arguments[0].equals("state")){
                    Future future;
                    if(Integer.decode(arguments[1]) > 0) {
                        future = FuturesCollection.get(Integer.decode(arguments[1]));
                    } else{
                        future = FuturesCollection.get(-Integer.decode(arguments[1])).getChildFuture();
                    }
                    if(future == null){
                        System.out.println("Task " + arguments[1] + " not found");
                    }
                    else if (arguments[0].equals("cancel")) {
                        future.cancel();
                        System.out.println("Task " + arguments[1] + " canceled");
                    }
                    else if (arguments[0].equals("state")) {
                        System.out.println("Task " + arguments[1] + " is " + future.getState().toString());
                    }
                }

                command = reader.readLine();
            }
            MyScheduler.shutDown();

        } catch (Exception e){}
    }
}
