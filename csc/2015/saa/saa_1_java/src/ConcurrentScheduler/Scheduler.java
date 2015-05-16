package ConcurrentScheduler;

import java.util.*;

/**
 * Created by Anastasia on 03.05.2015.
 */
public class Scheduler {
    HashMap<Integer, CleverThread> Threads;
    final Queue<Task> Tasks;
    final Dictionary<Integer, Task> TaskCollection;
    private int ThreadNumber;

    Scheduler(int threadNumber) {
        ThreadNumber = threadNumber;
        Tasks = new ArrayDeque<>();
        Threads = new HashMap<>();
        TaskCollection = new Hashtable<>();

        for (int i = 0; i < ThreadNumber; ++i) {
            CleverThread myCleverThread = new CleverThread();
            Threads.put((int) myCleverThread.getId(), myCleverThread);
            myCleverThread.start();
        }

    }

    public Future<?> submit(Task newTask) {
        Future future = new Future<>(newTask.ID, this);
        newTask.future = future;
        TaskCollection.put(newTask.ID, newTask);

        synchronized (Tasks) {
            Tasks.add(newTask);
            Tasks.notifyAll();
        }
        return future;
    }

    public void shutDown() {
        Collection<CleverThread> threadCollection = Threads.values();
        threadCollection.forEach(CleverThread::interrupt);
        System.out.println("all threads interrupted");
    }

    public class CleverThread extends Thread {
        private Task currentTask;

        public CleverThread() {
            currentTask = null;
        }

        @Override
        public void run() {

            while (true) {
                //Try to get the task from queue, checking if it is not waiting for its child task to finish
                while (currentTask == null) {
                    try {
                        synchronized (Tasks) {
                            if (Tasks.isEmpty()) {
                                Tasks.wait();
                            }
                            currentTask = Tasks.poll();
                            if (currentTask != null && currentTask.ChildTask != null && currentTask.ChildTask.future.getState().ordinal() < Future.State.Done.ordinal()) {
                                Tasks.offer(currentTask);
                                currentTask = null;
                            }
                        }
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                // Anti-cancelling if
                if (!currentTask.future.startRunning((int) this.getId()) && currentTask.future.getState() == Future.State.Canceled) {
                    currentTask = null;
                    continue;
                }
                System.out.println("thread " + this.getId() + " started task " + currentTask.ID);

                // If task is canceled, get rid of it (one more anti-cancelling if, just in case)
                if (Thread.interrupted()){
                    if (currentTask.future.getState() == Future.State.Canceled) {
                        currentTask = null;
                        continue;
                    }
                }

                // Try to run the task, if got runtime exception, need to release current task until
                // the child task is finished. Else got some other exception, should notify the user
                try {
                    currentTask.run();
                    currentTask.future.casState(Future.State.Running, Future.State.Done);
                } catch (RuntimeException e) {
                    Task newTask = new Task(currentTask.ID, currentTask.sleepLeft);
                    newTask.ChildTask = currentTask.ChildTask;
                    newTask.future = currentTask.future;
                    newTask.duration = currentTask.sleepLeft;

                    synchronized (Tasks) {
                        Tasks.offer(newTask);
                    }
                    synchronized (TaskCollection) {
                        TaskCollection.remove(newTask.ID);
                        TaskCollection.put(newTask.ID, newTask);
                    }
                } catch (Exception e) {
                    currentTask.future.setState(Future.State.ThrownException);
                    System.out.println(currentTask.ID + " thrown exception");
                }
                currentTask = null;
            }
        }
    }
}
