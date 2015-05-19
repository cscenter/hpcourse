/**
 * @author Ruslan Akhundov
 */
public class TaskFactory {

    private int currentId;

    public TaskFactory() {
        currentId = 0;
    }

    public class Task implements Runnable {
        private final long duration;
        private final int id;

        public Task(long duration, int id) {
            this.duration = duration;
            this.id = id;
        }

        public int getId() {
            return id;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(duration);
                System.out.println("Task " + id + " completed.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Creates a new task with specified duration
     * @param taskDuration amount of time in millis which task needs to complete.
     */
    public synchronized Task createTask(long taskDuration) {
        return new Task(taskDuration, ++currentId);
    }
}
