package client;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TestClient {
    private static int DEFAULT_PORT = 55555;
    private static String HOST = "localhost";
    private static int CLIENT_COUNT = 100;
    private static int lastClientId;
    private static final Random rand = new Random();
    private ConcurrentHashMap<Integer, Optional<Long>> calculatedValues = new ConcurrentHashMap<>();
    private AtomicInteger lastSubmitTaskId = new AtomicInteger();
    private AtomicInteger lastSubscribeTaskId = new AtomicInteger();
    private Object monitor = new Object();

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.valueOf(args[0]);
            } catch (NumberFormatException e) {

            }
        }
        new TestClient().start(HOST, port);
    }

    private String getClientId() {
        return String.valueOf(++lastClientId);
    }

    private long getRandomValue() {
        return rand.nextInt(10);
    }

    private void start(String host, int port) {
        for (int i = 0; i < CLIENT_COUNT; i++) {
            for (TaskType taskType : TaskType.values())
                new Worker(host, port, taskType, getClientId()).start();
        }
    }

    private class Worker extends Thread {
        private String host;
        private int port;
        private TaskType type;
        private String clientId;
        private long lastRequestId;

        public Worker(String host, int port, TaskType taskType, String clientId) {
            this.host = host;
            this.port = port;
            this.type = taskType;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            try {
                Socket socket = new Socket(host, port);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                switch (type) {
                    case INDEPENDENT_TASK:
                        submitIndependentTask(in, out);
                        break;
//                    case DEPENDENT_TASK:
//                        submitDependentTask(in, out);
//                        break;
                    case SUBSCRIBE_TASK:
                        subscribeTask(in, out);
                        break;
                    case LIST_TASK:
                        listTask(in, out);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void submitIndependentTask(InputStream in, OutputStream out) {
            while (true) {
                long a = getRandomValue();
                long b = getRandomValue();
                long p = getRandomValue();
                long m = getRandomValue();
                long n = getRandomValue();
                Protocol.WrapperMessage message = null;
                try {
                    Optional result = calculate(a, b, p, m, n);
                    long requestId = ++lastRequestId;
                    buildSubmitRequest(a, b, p, m, n, requestId).writeDelimitedTo(out);
                    message = Protocol.WrapperMessage.parseDelimitedFrom(in);
                    if (message.getResponse().getRequestId() != requestId) {
                        throw new RuntimeException("Wrong request id !");
                    }
                    if (message.getResponse().getSubmitResponse().getStatus() != Protocol.Status.OK) {
                        throw new RuntimeException("Wrong status!");
                    }
                    int submittedTaskId = message.getResponse().getSubmitResponse().getSubmittedTaskId();
                    if (calculatedValues.get(submittedTaskId) != null) {
                        throw new RuntimeException("Task id already exist!");
                    }
                    calculatedValues.put(submittedTaskId, result);
                    lastSubmitTaskId.incrementAndGet();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private Optional<Long> calculate(long a, long b, long p, long m, long n) {
            try {
                while (n-- > 0) {
                    b = (a * p + b) % m;
                    a = b;
                }
            } catch (ArithmeticException e) {
                return Optional.empty();
            }
            return Optional.of(a);
        }

        private Protocol.WrapperMessage buildSubmitRequest(long a, long b, long p, long m, long n, long requestId) {
            return Protocol.WrapperMessage.newBuilder().setRequest(
                    Protocol.ServerRequest.newBuilder()
                            .setClientId(clientId)
                            .setRequestId(requestId)
                            .setSubmit(Protocol.SubmitTask.newBuilder()
                                    .setTask(Protocol.Task.newBuilder()
                                            .setA(buildParam(a))
                                            .setB(buildParam(b))
                                            .setP(buildParam(p))
                                            .setM(buildParam(m))
                                            .setN(n)
                                            .build())
                                    .build())
                            .build())
                    .build();
        }

        private Protocol.Task.Param buildParam(long value) {
            return Protocol.Task.Param.newBuilder().setValue(value).build();
        }
//
//        private void submitDependentTask(InputStream in, OutputStream out) {
//            while (true) {
//                long a = getRandomValue();
//                int dependentTaskId = randomDependentTaskId();
//                long p = getRandomValue();
//                long m = getRandomValue();
//                long n = getRandomValue();
//                Protocol.WrapperMessage message = null;
//                try {
//                    long requestId = ++lastRequestId;
//                    buildSubmitRequest(a, dependentTaskId, p, m, n, requestId).writeDelimitedTo(out);
//                    message = Protocol.WrapperMessage.parseDelimitedFrom(in);
//                    if (message.getResponse().getRequestId() != requestId) {
//                        throw new RuntimeException("Wrong request id !!!!!!!!!!!!");
//                    }
//                    if (message.getResponse().getSubmitResponse().getStatus() != Protocol.Status.OK) {
//                        throw new RuntimeException("Wrong status!!!!!!!!!!");
//                    }
//                    int submittedTaskId = message.getResponse().getSubmitResponse().getSubmittedTaskId();
//                    if (calculatedValues.get(submittedTaskId) != null) {
//                        throw new RuntimeException("Task id already exist!!!!!!!!");
//                    }
//                    Optional<Long> optional = null;
//                    while (true) {
//                        try {
//                            sleep(10);
//                            optional = calculatedValues.get(dependentTaskId);
//                            if (optional != null) {
//                                break;
//                            }
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    if (!optional.isPresent()) {
//                        calculatedValues.put(submittedTaskId, Optional.empty());
//                    } else {
//                        calculatedValues.put(submittedTaskId, calculate(a, optional.get(), p, m, n));
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                try {
//                    sleep(10);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//
//        private Protocol.WrapperMessage buildSubmitDependentRequest(long a, int taskId, long p, long m, long n, long requestId) {
//            return Protocol.WrapperMessage.newBuilder().setRequest(
//                    Protocol.ServerRequest.newBuilder()
//                            .setClientId(clientId)
//                            .setRequestId(requestId)
//                            .setSubmit(Protocol.SubmitTask.newBuilder()
//                                    .setTask(Protocol.Task.newBuilder()
//                                            .setA(buildParam(a))
//                                            .setB(Protocol.Task.Param.newBuilder().setDependentTaskId(taskId))
//                                            .setP(buildParam(p))
//                                            .setM(buildParam(m))
//                                            .setN(n)
//                                            .build())
//                                    .build())
//                            .build())
//                    .build();
//        }
//
//
//        private int randomDependentTaskId() {
//            return lastTaskId.get() + 1 + rand.nextInt(50);
//        }

        private void subscribeTask(InputStream in, OutputStream out) {
            while (true) {
                try {
                    synchronized (monitor) {
                        while (lastSubmitTaskId.get() <= lastSubscribeTaskId.get() + 1) {
                            try {
                                sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    long requestId = ++lastRequestId;
                    int taskId = lastSubscribeTaskId.incrementAndGet();
                    if (calculatedValues.get(taskId) == null) {
                        continue;
                    }
                    buildSubscribeRequest(requestId, taskId).writeDelimitedTo(out);
                    Protocol.SubscribeResponse response = Protocol.WrapperMessage.parseDelimitedFrom(in).getResponse().getSubscribeResponse();
                    if (response.getStatus() == Protocol.Status.ERROR) {
                        if (calculatedValues.get(taskId).isPresent()) {
                            throw new RuntimeException("isPresent() must be false!");
                        }
                    } else {
                        if (calculatedValues.get(taskId) == null) {
                            System.out.println("!!!!!!!!!");
                        }
                        if (calculatedValues.get(taskId).get() != response.getValue()) {
                            throw new RuntimeException("Values on client and server is not equal!");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private Protocol.WrapperMessage buildSubscribeRequest(long requestId, int taskId) {
            return Protocol.WrapperMessage.newBuilder().setRequest(
                    Protocol.ServerRequest.newBuilder()
                            .setClientId(clientId)
                            .setRequestId(requestId)
                            .setSubscribe(Protocol.Subscribe.newBuilder()
                                    .setTaskId(taskId)
                                    .build())
                            .build())
                    .build();
        }

        private void listTask(InputStream in, OutputStream out) {
            while (true) {
                long requestId = ++lastRequestId;
                try {
                    buildListRequest(requestId).writeDelimitedTo(out);
                    Protocol.ListTasksResponse response = Protocol.WrapperMessage.parseDelimitedFrom(in).getResponse().getListResponse();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        private Protocol.WrapperMessage buildListRequest(long requestId) {
            return Protocol.WrapperMessage.newBuilder().setRequest(
                    Protocol.ServerRequest.newBuilder()
                            .setClientId(clientId)
                            .setRequestId(requestId)
                            .setList(Protocol.ListTasks.newBuilder().build())
                            .build())
                    .build();
        }

    }


    private enum TaskType {
        INDEPENDENT_TASK,
        DEPENDENT_TASK,
        SUBSCRIBE_TASK,
        LIST_TASK
    }
}
