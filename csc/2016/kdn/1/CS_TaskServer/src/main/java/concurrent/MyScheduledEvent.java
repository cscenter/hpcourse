package concurrent;

/**
 * Created by dkorolev on 4/9/2016.
 */
public class MyScheduledEvent {
    Thread thread;
    volatile boolean running;

    public MyScheduledEvent(Runnable runnable, int delay) {
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        synchronized (this) {
                            this.wait(delay);
                        }
                        if (running) {
                            try {
                                runnable.run();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void start() {
        thread.start();
    }

    public void stop() throws InterruptedException {
        running = false;
        synchronized (this) {
            this.notify();
        }
        thread.join();
    }
}
