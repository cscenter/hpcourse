import java.util.Scanner;
import java.util.concurrent.*;

/**
 * Created by alexander on 10.05.15.
 */
public class Main {
    public static void main(String[] args){
        String input;
        Scanner in = new Scanner(System.in);
        System.out.println("Type number of threads please:");
        int count_of_threads = Integer.parseInt(in.nextLine());
        My_scheduler sched = new My_scheduler(count_of_threads);
        System.out.println("Type: add [duration] , for creating new task for [duration] miliseconds");
        System.out.println("Type: status [id] , for getting status of task with id [id]");
        System.out.println("Type: cancel [id] , for cancelling status of task with id [id]");
        System.out.println("Type: exit , for exiting");
        while(true){
            System.out.println("Type command please:");
            input = in.nextLine();
            if (input.equals("exit"))
                break;
            if (input.startsWith("add")){
                int duration = Integer.parseInt(input.substring("add ".length()));
                Sleepy_task task = new Sleepy_task(duration);
                sched.submit(Executors.callable(task), task.get_id());
                System.out.println(task.get_id());
                continue;
            }
            if (input.startsWith("status")){
                int id = Integer.parseInt(input.substring("status ".length()));
                System.out.println(sched.get_Status(id));
                continue;
            }
            if (input.startsWith("cancel")){
                int id = Integer.parseInt(input.substring("cancel ".length()));
                try{
                    if (sched.cancel_id(id))
                        System.out.println("Task with id " + id + " was cancelled");
                    else
                        System.out.println("Task with id " + id + " was not cancelled");
                }
                catch (IllegalArgumentException e){
                    System.out.println("Incorrect id");
                }
                continue;
            }
        }

        sched.shut_down();



    }
    static class Sleepy_task implements Runnable {
        private static int counter = 0;
        private final int time_of_sleeping;
        private final int task_id;
        Sleepy_task(int time_of_sleeping) {
            this.time_of_sleeping = time_of_sleeping;
            this.task_id = counter;
            synchronized(this){
                counter ++;
            }
        }
        public void run(){
            try{
                Thread.sleep(time_of_sleeping);
            }
            catch (InterruptedException e){
                System.out.println("Sleepy_task with tsk_id " + task_id + " was interrupted.");
            }
        }
        public int get_id(){
            return task_id;
        }
    }
}

/*    static class Product_of_sines_task implements Callable<Double> {
        private static volatile int counter = 0;
        private int task_id;
        private int bound;
        Product_of_sines_task(int bound) {
            this.bound = bound;
            task_id = counter;
            counter ++;
        }
        public Double call() {
            double res = 1;
            for (int i = 1; i<= bound; i++){
                res *= Math.sin(i);
                if (Thread.interrupted()){
                    System.out.println("Product_of_sines_task with tsk_id " + task_id + " was interrupted.");
                    return -1.1;
                }
            }
            return res;
        }
        public int get_id(){
            return task_id;
        }
    }
}*/
