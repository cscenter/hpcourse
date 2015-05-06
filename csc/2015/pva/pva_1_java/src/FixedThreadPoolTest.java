import org.junit.Test;
import ru.compscicenter.ThreadPool.FixedThreadPool;
import ru.compscicenter.ThreadPool.LivingThread;
import ru.compscicenter.ThreadPool.Task;
import ru.compscicenter.ThreadPool.TaskFuture;
import ru.compscicenter.ThreadPool.TestTasks.*;
import sun.net.ConnectionResetException;

import static org.junit.Assert.*;

/**
 * Created by Flok on 03.05.2015.
 */
public class FixedThreadPoolTest {
    @Test(timeout = 2000)
    public void stopTest() throws InterruptedException {
        FixedThreadPool ftp = new FixedThreadPool(2);
        ftp.submit(new CountingTask());
        ftp.submit(new CountingTask());
        ftp.submit(new CountingTask());
        ftp.submit(new CountingTask());
        ftp.submit(new CountingTask());
        ftp.stop();
        Thread.sleep(1000);
        for(LivingThread thread: ftp.getLivingThreads()) {
            Thread.State state = thread.getState();
            assert(state.equals(Thread.State.TERMINATED));
        }
    }

    @Test
    public void commonTaskTest() {
        FixedThreadPool ftp = new FixedThreadPool(4);
        Integer delay = 50;
        Task task = new SleepingTask(delay);
        TaskFuture<Integer> future = ftp.submit(task);
        Integer result = future.get();
        ftp.stop();
        assertEquals(result, delay);
    }

    @Test
    public void multipleTasksTest() {
        int tasksAmount = 10;
        FixedThreadPool ftp = new FixedThreadPool(4);
        TaskFuture [] futures = new TaskFuture[tasksAmount];
        for(int i = 0; i < tasksAmount; i++) {
            futures[i] = ftp.submit(new SleepingTask(1000));
        }

        for(int i = 0; i < tasksAmount; i++) {
            futures[i].get();
        }
        ftp.stop();
    }

    @Test
    public void recursiveTaskTest() {
        FixedThreadPool ftp = new FixedThreadPool(2);
        Integer delay = 1000;
        TaskFuture<Integer> future = ftp.submit(new RecursiveTask(ftp, delay));
        Integer result = future.get();
        ftp.stop();
        assertEquals(result, delay);
    }

    @Test
    public void exceptionTest() {
        FixedThreadPool ftp = new FixedThreadPool(2);
        TaskFuture<Integer> sleeping = ftp.submit(new SleepingTask(1000));
        TaskFuture throwing = ftp.submit(new ExceptionTask());
        sleeping.get();
        throwing.get();
        ftp.stop();
        assertEquals(sleeping.getException(), null);
        assert(throwing.getException() instanceof ConnectionResetException);
    }

    /*
        Тестируется возможность решать новые задачи, пока ожидаемую в get задачу решает другой поток.
     */
    @Test(timeout = 16000)
    public void recursiveTestWithAlienTask() {
        FixedThreadPool ftp = new FixedThreadPool(2);
        TaskFuture<Integer> recursive = ftp.submit(new RecursiveTask(ftp, 15000));
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        TaskFuture<Integer> sleeping = ftp.submit(new SleepingTask(5000));
        TaskFuture<Integer> sleeping2 = ftp.submit(new SleepingTask(5000));
        Integer res1 = recursive.get();
        Integer res2 = sleeping.get();
        Integer res3 = sleeping2.get();
        ftp.stop();
        assert(res1.equals(15000));
        assert(res2.equals(5000));
        assert(res3.equals(5000));
    }


    /*
        Тест на 5 минут.
        Долгие задержки нужны, чтобы ожидаемая последовательность событий
        случилась наверняка.

        пытаемся поставить задачу и успеть отменить её до выполнения. Смотрим, что ничего не упало.
        пытаемся поставить задачу и отменить её в процессе выполнения. Смотрим, что ничего не упало и задача завершилась.
     */
    @Test(timeout = 50 * 6000)
    public void cancelTest() {
        FixedThreadPool ftp = new FixedThreadPool(2);
        for(int i = 0; i < 50; i++) {
            TaskFuture<Integer> sleeping = ftp.submit(new CountingTask(10000000));
            sleeping.cancel(false);
            assert (sleeping.isDone());
            assert (sleeping.isCancelled());
            sleeping = ftp.submit(new CountingTask(10000000));
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {

            }
            sleeping.cancel(true);
            assert (sleeping.isDone());
            assert (sleeping.isCancelled());
        }
        ftp.stop();
    }

    @Test(timeout = 16000)
    public void recursiveCancelTest() {
        FixedThreadPool ftp = new FixedThreadPool(2);
        TaskFuture<Integer> recursive = ftp.submit(new RecursiveSlowTask(ftp, 60000));
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {

        }
        recursive.cancel(true);
        assert(recursive.isCancelled());
    }

}