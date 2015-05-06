import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by philipp on 29.04.15.
 */
public class ConcurrentScheduler {

    private volatile int noThreads;

    private final List<Worker> workers = new ArrayList<>();
    private final Deque<Work> worksDeque = new ArrayDeque<>();

    private final Dictionary<Integer, Work> works = new Hashtable<>();
    private final Map<Long, Worker> threadsOfWorkers = new Hashtable<>();

    private volatile boolean isRun = true;

    private final Object TASKS = new Object();
    private final Object FUTURES = new Object();

    enum Status {
        NOT_STARTED, IN_PROGRESS, CANCELLED, DONE
    }


    ConcurrentScheduler(int threads) {
        this.noThreads = threads;
        initWorkers();
    }

    private void initWorkers() {
        for (int i = 0; i < noThreads; ++i) {
            Worker worker = new Worker(i);
            worker.setName("Worker" + Integer.toString(i));
            workers.add(worker);
            worker.start();
        }

    }

    private void addWork(Work work) {
        synchronized (TASKS) {
            worksDeque.add(work);
            TASKS.notifyAll();
        }
    }

    public Future addTask (Task task, Integer taskID) throws AlreadyExistIDException {

        if (works.get(taskID) != null) {
            throw new AlreadyExistIDException("Task with id=" + Integer.toString(taskID) + " already used");
        }

        Future future = new Future(taskID);
        Work work = new Work(task, taskID, future);

        synchronized (FUTURES) {
            if (works.get(taskID) != null) {
                throw new AlreadyExistIDException("Task with id=" + Integer.toString(taskID) + " already used");
            }
            works.put(taskID, work);
        }


        addWork(work);

        return future;
    }

    public void shutDown() {
        isRun = false;
        for (Worker worker : workers) {
            worker.interrupt();
        }
    }

    protected void tryGiveFreedom(long threadID, Work childWork) {
        if (threadsOfWorkers.containsKey(threadID)) {
            int workerID = threadsOfWorkers.get(threadID).workerID;
            Worker worker = workers.get(workerID);
            worker.currentWork.childWork = childWork;// указываем подзадачу
            throw new NeedMoveWorkException();
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

            threadsOfWorkers.put(getId(), this);

            while (isRun) {
                synchronized (TASKS) {
                    try {
                        while (worksDeque.isEmpty()) {
                            TASKS.wait();
                        }

                        currentWork = worksDeque.poll();

                        Status statusChildWork;
                        if (currentWork.childWork != null) {
                            statusChildWork = currentWork.childWork.future.getStatus();
                        }
                        else {
                            statusChildWork = Status.DONE;
                        }
                        if (statusChildWork != Status.DONE && statusChildWork != Status.CANCELLED) { // если у этой задачи не выполнена подзадача или не отменена, то кладём в очередь обратно,
                            worksDeque.offer(currentWork);
                            continue; // и пытаемся по новой взять задачу
                        } else {
                            currentWork.future.setWorkerID(workerID);// иначе берём работу под роспись :)
                        }
                    } catch (InterruptedException e) {
                        if (!isRun) {
                            return; //shutdown
                        }

                    }
                }

                if (!isRun) {
                    return; //shutdown
                }

                currentWork.future.compareAndSet(Status.NOT_STARTED, Status.IN_PROGRESS);

                try {
                    currentWork.task.run();
                } catch (NeedMoveWorkException e) {
                    Work remainingWork = new Work(currentWork);

                    synchronized (TASKS) {
                        if (!isInterrupted()) {
                            worksDeque.offer(remainingWork);
                            works.put(remainingWork.taskID, remainingWork);
                            continue;
                        }
                    }
                }
                synchronized (currentWork){} // чтобы не перешёл на следующую, если пытаемся отменить выполняемую им задачу
                if (interrupted()) {
                    works.get(currentWork.taskID).future.setStatus(Status.CANCELLED);
                }
                else {
                    works.get(currentWork.taskID).future.setStatus(Status.DONE);
                }
            }
        }

    }


    private class Work {

        public Task task;
        public int taskID;
        public Future future;
        public Work childWork;

        public Work(Task task, int taskID, Future future) {
            this.task = task;
            this.taskID = taskID;
            this.future = future;
        }

        public Work(Work work) {
            task = new Task(work.task.miliSeconds, work.task.name);
            taskID = work.taskID;
            future = work.future;
            this.future.deleteWorkerID();
            this.childWork = work.childWork;
        }
    }

    public class Future {

        private volatile Status status;
        private volatile Integer taskID;
        private volatile Integer workerID;
        private volatile AtomicBoolean FreedomGiven;
        protected volatile boolean CancellInProgress;
        private final Object CANCELLATION = new Object();

        protected Future(Integer taskID) {
            this.taskID = taskID;
            status = Status.NOT_STARTED;
            CancellInProgress = false;
            FreedomGiven = new AtomicBoolean(false);
        }

        protected void setWorkerID(Integer ID) {
            workerID = ID;
        }
        protected void deleteWorkerID() {workerID = null;};

        protected synchronized boolean compareAndSet(Status expected, Status set) {
            if (status == expected) {
                status = set;
                return true;
            }
            return false;
        }

        public void waitResult() {

            if (FreedomGiven.compareAndSet(false, true)) {
                long parent = Thread.currentThread().getId();
                tryGiveFreedom(parent, works.get(taskID));
            }
            //если вызвали из потока threadpoolа, то вылетит эксепшн, и дальнейший код не выполнится

            synchronized (this) {
                while (!(status.equals(Status.CANCELLED) || status.equals(Status.DONE))) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace(); // по идее наверное надо просто пробрасывать
                    }
                }
            }


        }


        public void cancell() {
            if (CancellInProgress) {
                return;
            }

            synchronized (CANCELLATION) {
                if (CancellInProgress) {
                    return;
                }
                CancellInProgress = true;
            }

            synchronized (works.get(taskID)) { // поставили "препятствие", чтобы воркер выполняющий отменяемую задачу, не перешёл к следующей
                synchronized (TASKS) {
                    if (workerID == null) { //если ещё не назначили работника на эту задачу, мы блокируем очередь задач чтобы никто оттуда эту задачу не забрал, и удаляем
                        worksDeque.remove(works.get(taskID));
                        setStatus(Status.CANCELLED);
                        return;
                    } else {
                        Worker worker = workers.get(workerID);


                            if (worker.currentWork.taskID == taskID) { //если воркер выполняет задачу которую и отменяем, то прерываем его работу
                                worker.interrupt();
                            }
                    }
                }
            }

            if (works.get(taskID).childWork != null) {
                works.get(taskID).childWork.future.cancell();
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

    class NeedMoveWorkException extends RuntimeException {}
    class AlreadyExistIDException extends Exception {
        AlreadyExistIDException (String s) {
            super(s);
        }
    }
}

