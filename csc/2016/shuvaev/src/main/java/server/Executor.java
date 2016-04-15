package server;

import communication.Protocol;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Executor {
    private static final int THREAD_COUNT = 16;
    private static final int MONITOR_COUNT = 256;
    private static final Executor INSTANCE = new Executor(THREAD_COUNT);
    private Object mainMonitor = new Object();
    private Registry registry = new Registry();
    private LinkedList<Task> tasks = new LinkedList();
    private AtomicInteger lastTaskId = new AtomicInteger();
    private Object[] monitors = new Object[MONITOR_COUNT];
    private Random random = new Random();
    private AtomicInteger countReadyThreads = new AtomicInteger();
    private AtomicInteger countThreads = new AtomicInteger();


    private Executor(int threadCount) {
        for (int i = 0; i < MONITOR_COUNT; i++) {
            monitors[i] = new Object();
        }
        for (int i = 0; i < threadCount; i++) {
            addWorker();
        }
    }

    public static Executor getInstance() {
        return INSTANCE;
    }

    private void addWorker() {
        new Worker().start();
        countThreads.incrementAndGet();
    }

    public Future submit(Protocol.WrapperMessage message, Object monitor) {
        Future future = new Future();
        Task task = new Task(message, future, monitor);
        synchronized (mainMonitor) {
            addTask(task);
            if (countReadyThreads.get() == 0) {
                addWorker();
            }
            mainMonitor.notifyAll();
        }
        return future;
    }

    private void addTask(Task task) {
        tasks.add(task);
    }

    private Object getMonitorByTaskId(int taskId) {
        return monitors[taskId % MONITOR_COUNT];
    }

    private void notifyByTaskId(int taskId) {
        Object monitor = getMonitorByTaskId(taskId);
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    private void waitByTaskId(int taskId) {
        Object monitor = getMonitorByTaskId(taskId);
        synchronized (monitor) {
            try {
                monitor.wait(1000 + random.nextInt(100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class Worker extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Task task = null;
                    synchronized (mainMonitor) {
                        countReadyThreads.incrementAndGet();
                        mainMonitor.wait();
                        countReadyThreads.decrementAndGet();
                        task = getTask();
                    }
                    if (task != null) {
                        processTask(task);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (mainMonitor) {
                    if (countThreads.get() > THREAD_COUNT) {
                        countThreads.decrementAndGet();
                        return;
                    }
                }
            }
        }

        private Task getTask() {
            if (tasks.size() != 0) {
                return tasks.removeFirst();
            }
            return null;
        }

        private void processTask(Task task) {
            Protocol.ServerRequest request = task.getMessage().getRequest();

            if (request.hasSubmit()) {
                int taskId = lastTaskId.incrementAndGet();
                registry.addTask(taskId, request.getClientId(), request.getSubmit().getTask());
                task.getFuture().setMessage(buildSubmitResponse(taskId, request.getRequestId()));
                synchronized (task.getMonitor()) {
                    task.getMonitor().notify();
                }
                processSubmit(task, taskId);
            }
            if (request.hasSubscribe()) {
                task.getFuture().setMessage(buildSubscribeResponse(request.getSubscribe().getTaskId(), request.getRequestId()));
                synchronized (task.getMonitor()) {
                    task.getMonitor().notify();
                }
            }
            if (request.hasList()) {
                task.getFuture().setMessage(buildListResponse(request.getRequestId()));
                synchronized (task.getMonitor()) {
                    task.getMonitor().notify();
                }
            }
        }

        private void processSubmit(Task submitTask, int taskId) {
            Protocol.Task task = submitTask.getMessage().getRequest().getSubmit().getTask();
            try {

                long a = getParam(task.getA());
                long b = getParam(task.getB());
                long m = getParam(task.getM());
                long p = getParam(task.getP());
                long n = task.getN();

                long result = calculate(a, b, m, p, n);
                registry.addValue(taskId, Optional.of(result));
            } catch (ArithmeticException e) {
                registry.addValue(taskId, Optional.empty());
            }
            notifyByTaskId(taskId);
        }

        private long calculate(long a, long b, long m, long p, long n) {
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            return a;
        }

        private long getParam(Protocol.Task.Param param) {
            if (param.hasValue()) {
                return param.getValue();
            } else {
                Optional<Long> optional = getDependentValue(param.getDependentTaskId());
                if (!optional.isPresent()) {
                    throw new ArithmeticException();
                }
                return optional.get();
            }
        }

        private Optional<Long> getDependentValue(int taskId) {
            Optional<Long> value = null;
            while ((value = registry.getValue(taskId)) == null) {
                waitByTaskId(taskId);
            }
            return value;
        }

        private Protocol.WrapperMessage buildSubmitResponse(int submittedTaskId, long requestId) {
            Protocol.SubmitTaskResponse response = Protocol.SubmitTaskResponse.newBuilder()
                    .setStatus(Protocol.Status.OK)
                    .setSubmittedTaskId(submittedTaskId)
                    .build();
            Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                    .setResponse(Protocol.ServerResponse.newBuilder()
                            .setRequestId(requestId)
                            .setSubmitResponse(response)
                            .build())
                    .build();
            return message;
        }

        private Protocol.WrapperMessage buildSubscribeResponse(int taskId, long requestId) {
            Optional<Long> value = getDependentValue(taskId);
            Protocol.SubscribeResponse.Builder builder = Protocol.SubscribeResponse.newBuilder();
            if (value.isPresent()) {
                builder.setStatus(Protocol.Status.OK);
                builder.setValue(value.get());
            } else {
                builder.setStatus(Protocol.Status.ERROR);
            }

            Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder()
                    .setResponse(Protocol.ServerResponse.newBuilder()
                            .setRequestId(requestId)
                            .setSubscribeResponse(builder.build())
                            .build())
                    .build();
            return message;
        }

        private Protocol.WrapperMessage buildListResponse(long requestId) {
            Protocol.ListTasksResponse.TaskDescription.Builder builder = Protocol.ListTasksResponse.TaskDescription.newBuilder();
            for (Protocol.ListTasksResponse.TaskDescription taskDescription : registry.getAllTasks()) {
                builder.setTask(taskDescription.getTask())
                        .setTaskId(taskDescription.getTaskId())
                        .setClientId(taskDescription.getClientId());
                Optional<Long> value = registry.getValue(taskDescription.getTaskId());
                if (value != null && value.isPresent()) {
                    builder.setResult(value.get());
                }
            }
            return Protocol.WrapperMessage.newBuilder()
                    .setResponse(Protocol.ServerResponse.newBuilder()
                            .setRequestId(requestId)
                            .setListResponse(Protocol.ListTasksResponse.newBuilder()
                                   .addTasks(builder.build())
                                    .setStatus(Protocol.Status.OK)
                                    .build())
                            .build())
                    .build();
        }
    }
}
