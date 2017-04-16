package server;

import communication.Protocol;
import concurrent.*;

import java.util.*;

/**
 * Created by dkorolev on 4/2/2016.
 */
public class TaskManager {

    final MyAtomicInteger taskIdProducer;
    final MyAtomicInteger checkingForResultsIndicator;
    final Map<Integer, TaskDescFull> resultMap;
    final Map<Integer, List<TaskSubscriber>> taskSubscribers;
    final Map<Integer, AsyncResult<Long>> futureResults;
    final MyExecutorService executorService;
    final MyScheduledEvent checkingForResults;

    public TaskManager(int nThreads, int delayInSeconds) {
        taskIdProducer = new MyAtomicInteger();
        checkingForResultsIndicator = new MyAtomicInteger();
        resultMap = new HashMap<>();
        taskSubscribers = new HashMap<>();
        futureResults = new HashMap<>();
        executorService = MyExecutorService.newFixedThreadPool(nThreads);
        checkingForResults = new MyScheduledEvent(() -> {
            try {
                checkForResults();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, delayInSeconds);
        checkingForResults.start();
    }

    public int submitTask(TaskDescFull taskDesc) {
        Protocol.Task task = taskDesc.task;
        int taskId = taskIdProducer.getAndIncrement();
        synchronized (this.resultMap) {
            this.resultMap.put(taskId, taskDesc);
        }
        TaskCallable taskCallable = new TaskCallable(taskId);
        setParamOrSubscribe(TaskCallable.ParamType.A, task.getA(), taskCallable);
        setParamOrSubscribe(TaskCallable.ParamType.B, task.getB(), taskCallable);
        setParamOrSubscribe(TaskCallable.ParamType.M, task.getM(), taskCallable);
        setParamOrSubscribe(TaskCallable.ParamType.P, task.getP(), taskCallable);

        boolean allSet = taskCallable.setParam(TaskCallable.ParamType.N, task.getN());
        if (allSet) {
            submit(taskCallable);
        }

        return taskId;
    }

    public Map<Integer, TaskDescFull> getList() {
        return getResultMapSnapshot();
    }

    public Long getResult(int taskId) throws InterruptedException {
        TaskDescFull taskDescFull = getTaskDescFull(taskId);
        if (taskDescFull == null)
            return null;

        if (taskDescFull.result == null) {
            synchronized (taskDescFull) {
                while (!taskDescFull.completed) {
                    taskDescFull.wait();
                }
            }
        }

        return taskDescFull.result;
    }

    public void stop() {
        try {
            //System.out.println("TaskManager finishing");
            executorService.awaitTerminationSkipQueried();
            //System.out.println("TaskManager executorService finished");
            checkingForResults.stop();
            //System.out.println("TaskManager checkingForResults finished");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private TaskDescFull getTaskDescFull(int taskId) {
        synchronized (resultMap) {
            return resultMap.get(taskId);
        }
    }

    private void checkForResults() throws InterruptedException {
        //only one thread should execute this method at once
        if (checkingForResultsIndicator.getAndIncrement() == 0) {
            try {
                Map<Integer, AsyncResult<Long>> futureResults = getFutureResultMapSnapshot();
                Map<Integer, Long> localResultMap = new HashMap<>();
                for (Map.Entry<Integer, AsyncResult<Long>> futureEntry : futureResults.entrySet()) {
                    if (futureEntry.getValue().isDone()) {
                        Long result;
                        try {
                            result = futureEntry.getValue().getResult();
                        } catch (MyExecutionException e) {
                            result = null;
                            //better to log id
                            e.printStackTrace();
                        }
                        localResultMap.put(futureEntry.getKey(), result);
                    }
                }
                removeFromFutures(localResultMap.keySet());
                addToResults(localResultMap);
                List<TaskSubscriber> subscribersReadyForSubmit = notifySubscribers(localResultMap);
                submit(subscribersReadyForSubmit);
            } finally {
                checkingForResultsIndicator.getAndDecrement();
            }
        } else {
            checkingForResultsIndicator.getAndDecrement();
        }
    }

    private List<TaskSubscriber> notifySubscribers(Map<Integer, Long> localResultMap) {
        Map<List<TaskSubscriber>, Long> subscribersOfResult = new HashMap<>(localResultMap.size());
        int totalNumberNotifiers = 0;
        synchronized (taskSubscribers) {
            for (Map.Entry<Integer, Long> resultEntry : localResultMap.entrySet()) {
                List<TaskSubscriber> removedSubscribers = taskSubscribers.remove(resultEntry.getKey());
                if (removedSubscribers != null) {
                    subscribersOfResult.put(removedSubscribers, resultEntry.getValue());
                    totalNumberNotifiers += removedSubscribers.size();
                }
            }
        }
        List<TaskSubscriber> subscribersReadyForSubmit = new ArrayList<>(totalNumberNotifiers);
        for (Map.Entry<List<TaskSubscriber>, Long> subscribersEntry : subscribersOfResult.entrySet()) {
            for (TaskSubscriber subscriber : subscribersEntry.getKey()) {
                boolean allSet = subscriber.taskCallable.setParam(subscriber.paramType, subscribersEntry.getValue());
                if (allSet) {
                    subscribersReadyForSubmit.add(subscriber);
                }
            }
        }

        return subscribersReadyForSubmit;
    }

    private void submit(List<TaskSubscriber> subscribers) {
        Map<Integer, AsyncResult<Long>> localFutureResults = new HashMap<>(subscribers.size());
        for (TaskSubscriber subscriber : subscribers) {
            AsyncResult<Long> futureResult = executorService.submit(subscriber.taskCallable);
            localFutureResults.put(subscriber.taskCallable.taskId, futureResult);
        }
        synchronized (futureResults) {
            for (Map.Entry<Integer, AsyncResult<Long>> futureEntry : localFutureResults.entrySet()) {
                futureResults.put(futureEntry.getKey(), futureEntry.getValue());
            }
        }
    }

    private void submit(TaskCallable taskCallable) {
        AsyncResult<Long> futureResult = executorService.submit(taskCallable);
        synchronized (futureResults) {
            futureResults.put(taskCallable.taskId, futureResult);
        }
    }

    private void addToResults(Map<Integer, Long> localResultMap) {
        synchronized (resultMap) {
            for (Map.Entry<Integer, Long> resultEntry : localResultMap.entrySet()) {
                TaskDescFull taskDescFull = resultMap.get(resultEntry.getKey());
                synchronized (taskDescFull) {
                    taskDescFull.result = resultEntry.getValue();
                    taskDescFull.completed = true;
                    taskDescFull.notifyAll();
                }
            }
        }
    }

    private void removeFromFutures(Set<Integer> taskIds) {
        synchronized (futureResults) {
            for (Integer taskId : taskIds) {
                futureResults.remove(taskId);
            }
        }
    }

    private Map<Integer, TaskDescFull> getResultMapSnapshot() {
        Map<Integer, TaskDescFull> resultMap = new HashMap<>(2 * this.resultMap.size());
        synchronized (this.resultMap) {
            resultMap.putAll(this.resultMap);
        }
        return resultMap;
    }

    private Map<Integer, AsyncResult<Long>> getFutureResultMapSnapshot() {
        Map<Integer, AsyncResult<Long>> futureResults = new HashMap<>(2 * this.futureResults.size());
        synchronized (this.futureResults) {
            futureResults.putAll(this.futureResults);
        }
        return futureResults;
    }

    private void setParamOrSubscribe(TaskCallable.ParamType paramType, Protocol.Task.Param param, TaskCallable taskCallable) {
        if (param.hasValue()) {
            taskCallable.setParam(paramType, param.getValue());
        } else {
            setResultOrSubscribe(new TaskSubscriber(paramType, param.getDependentTaskId(), taskCallable));
        }
    }

    private boolean setResultOrSubscribe(TaskSubscriber taskSubscriber) {
        //return true if all set (actually does not matter)
        TaskDescFull taskDescFull = getTaskDescFull(taskSubscriber.taskId);
        if (taskDescFull == null) {
            return taskSubscriber.taskCallable.setParam(taskSubscriber.paramType, null);
        }

        if (!taskDescFull.completed) {
            List<TaskSubscriber> emptyList = new ArrayList<>();
            synchronized (taskDescFull) {
                if (!taskDescFull.completed) {
                    synchronized (taskSubscribers) {
                        List<TaskSubscriber> subscribers = taskSubscribers.get(taskSubscriber.taskId);
                        if (subscribers == null) {
                            subscribers = emptyList;
                            taskSubscribers.put(taskSubscriber.taskId, subscribers);
                        }
                        subscribers.add(taskSubscriber);
                    }
                }
            }
        }

        if (taskDescFull.completed) {
            return taskSubscriber.taskCallable.setParam(taskSubscriber.paramType, taskDescFull.result);
        }

        return false;
    }
}
