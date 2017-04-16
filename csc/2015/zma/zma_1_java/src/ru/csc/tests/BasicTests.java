package ru.csc.tests;

import org.junit.Assert;
import org.junit.Test;
import ru.csc.concurrent.ThreadPool;
import ru.csc.concurrent.ThreadPoolTask;

import java.util.concurrent.ExecutionException;

public class BasicTests {
    @Test(timeout = 2000)
    public void checkTaskIsEnding() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        int id = pool.submit(ThreadPoolTask.createTimedTask(1));
        ThreadPoolTask<?> task = pool.getTaskById(id);
        task.get();
    }

    @Test
    public void checkStatusIsDoneAfterTaskEnding() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        int id = pool.submit(ThreadPoolTask.createTimedTask(1));
        ThreadPoolTask<?> task = pool.getTaskById(id);
        task.get();
        Assert.assertTrue(task.isDone());
    }

    @Test
    public void checkStatusAfterTaskCancellation() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        int id = pool.submit(ThreadPoolTask.createTimedTask(10));
        pool.interrupt(id);
        ThreadPoolTask<?> task = pool.getTaskById(id);
        Assert.assertTrue(task.isCancelled());
    }

//    One second
    @Test(timeout = 1000)
    public void checkTaskIsCancelling() {
        ThreadPool pool = new ThreadPool(2);
        int id = pool.submit(ThreadPoolTask.createTimedTask(10));
        pool.interrupt(id);
        ThreadPoolTask<?> task = pool.getTaskById(id);

        try {
            task.get();
        } catch (InterruptedException | ExecutionException e) {
//            skip
        }
    }

//    All time execution ~ 4 seconds
    @Test(timeout = 3000)
    public void twoTasksWithTwoThreads() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        int id1 = pool.submit(ThreadPoolTask.createTimedTask(2));
        int id2 = pool.submit(ThreadPoolTask.createTimedTask(2));
        ThreadPoolTask<?> task1 = pool.getTaskById(id1);
        ThreadPoolTask<?> task2 = pool.getTaskById(id2);
        task1.get();
        task2.get();
    }

    @Test(timeout = 3000)
    public void tasksWithCancellationAndContinuation() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(2);
        int id1 = pool.submit(ThreadPoolTask.createTimedTask(10));
        int id2 = pool.submit(ThreadPoolTask.createTimedTask(10));
        int id3 = pool.submit(ThreadPoolTask.createTimedTask(2));
        int id4 = pool.submit(ThreadPoolTask.createTimedTask(2));
        pool.interrupt(id1);
        pool.interrupt(id2);
        ThreadPoolTask<?> task3 = pool.getTaskById(id3);
        ThreadPoolTask<?> task4 = pool.getTaskById(id4);
        task3.get();
        task4.get();
    }

    @Test(timeout = 3000)
    public void taskWithInnerTask() throws ExecutionException, InterruptedException {
        ThreadPool pool = new ThreadPool(1);
        int id = pool.submit(() -> {
            int innerId = pool.submit(ThreadPoolTask.createTimedTask(2));
            ThreadPoolTask<?> innerTask = pool.getTaskById(innerId);
            try {
                innerTask.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        ThreadPoolTask<?> task = pool.getTaskById(id);
        task.get();
    }
}
