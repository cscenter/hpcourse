import java.util.*;

/**
 * Created by philipp on 29.04.15.
 */
public class ConcurrentScheduler {

    private volatile int noThreads;

    private volatile List<Worker> workers;
    private volatile Deque<Work> worksDeque;

    private volatile Dictionary<Integer, Work> works;

    private volatile boolean isRun;

    private final Object TASKS = new Object();
    private final Object FUTURES = new Object();

    enum Status {
        DONE, CANCELLED, INPROGRESS, NOTSTARTED
    }


    ConcurrentScheduler(int threads) {
        isRun = true;
        worksDeque = new ArrayDeque<>();
        works = new Hashtable<>();
        this.noThreads = threads;

        initWorkers();
    }

    private void initWorkers() {
        workers = new ArrayList<>();
        for (int i = 0; i < noThreads; ++i) {
            Worker worker = new Worker(i);
            worker.setName("Worker" + Integer.toString(i));
            workers.add(worker);
            worker.start();
        }

    }

    public Future addTask (Runnable task, Integer taskID) throws Exception{

        if (works.get(taskID) != null) {
            throw new Exception("Task with id=" + Integer.toString(taskID) + " already used");
        }

        Future future = new Future(taskID);
        Work work = new Work(task, taskID, future);

        synchronized (FUTURES) {
            if (works.get(taskID) != null) {
                throw new Exception("Task with id=" + Integer.toString(taskID) + " already used");
            }
            works.put(taskID, work);
        }


        synchronized (TASKS) {
            worksDeque.add(work);
            TASKS.notifyAll();
        }

        return future;
    }

    public void shutDown() {
        isRun = false;
        for (Worker worker : workers) {
            worker.interrupt();
        }
    }

    class Worker extends Thread {

        private Integer workerID;
        private Work currentWork;

        public Worker(Integer ID) {
            super();
            currentWork = null;
            workerID = ID;
        }

        @Override
        public void run() {

            while (isRun) {
                synchronized (TASKS) {
                    try {
                        while (worksDeque.isEmpty()) {
                            TASKS.wait();
                        }
                        currentWork = worksDeque.poll();
                        currentWork.future.setWorkerID(workerID);// берём работу под роспись :)
                    } catch (InterruptedException e) {
                        if (!isRun) {
                            return; //shutdown
                        }

                    }
                }

                if (!isRun) {
                    return; //shutdown
                }
                currentWork.future.setStatus(Status.INPROGRESS);
                currentWork.task.run();
                if (interrupted()) {
                    works.get(currentWork.taskID).future.setStatus(Status.CANCELLED);
                }
                else {
                    works.get(currentWork.taskID).future.setStatus(Status.DONE);
                }
                synchronized (currentWork){}// чтобы не перешёл на следующую, пока пытаемся отменить выполняемую им задачу
            }
        }

    }


    private class Work {
        public Runnable task;
        public int taskID;
        public Future future;

        public Work(Runnable task, int taskID, Future future) {
            this.task = task;
            this.taskID = taskID;
            this.future = future;
        }
    }

    public class Future {

        private volatile Status status;
        private volatile Integer taskID;
        private volatile Integer workerID;
        protected volatile boolean CancellInProgress;

        protected Future(Integer taskID) {
            this.taskID = taskID;
            status = Status.NOTSTARTED;
            CancellInProgress = false;
        }

        protected void setWorkerID(Integer ID) {
            workerID = ID;
        }

//        protected synchronized boolean compareAndSet(Status expected, Status set) {
//            if (status == expected) {
//                status = set;
//                return true;
//            }
//            return false;
//        }

        public void waitResult() {
            synchronized (this) {
                while (!(status.equals(Status.CANCELLED) || status.equals(Status.DONE))) {
                    try {
                        wait();
                    }
                    catch (Exception e) {
                        e.printStackTrace(); // по идее наверное надо просто пробрасывать
                    }
                }
            }
        }

        public void cancell() {
            if (CancellInProgress) {
                return;
            }

            synchronized (this) {
                if (CancellInProgress) {
                    return;
                }
                CancellInProgress = true;
            }

            if (workerID == null) { //если ещё не назначили работника на эту задачу, мы блокируем очередь задач чтобы никто оттуда эту задачу не забрал, и удаляем
                synchronized (TASKS) {
                    if (workerID == null)
                    worksDeque.remove(works.get(taskID));
                    setStatus(Status.CANCELLED);
                    return;
                }
            }

            Worker worker = workers.get(workerID);

            synchronized (works.get(taskID)) { // поставили "препятствие", чтобы воркер выполняющий отменяемую задачу, не перешёл к следующей

                if (worker.currentWork.taskID == taskID) { //если воркер выполняет задачу которую и отменяем, то прерываем его работу
                    worker.interrupt();
                }
            }


        }

        public Status getStatus() {
            return status;
        }

        protected void setStatus(Status status) {
            this.status = status;
            synchronized (this) {
                notifyAll();
            }
        }


    }
}

