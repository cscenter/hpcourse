package communication;

import com.google.protobuf.GeneratedMessage;
import javafx.util.Pair;
import util.SynchronizedHashMap;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static communication.Protocol.*;

public class Server extends Thread {

    private static SynchronizedHashMap<Integer, TaskThread> taskMap = new SynchronizedHashMap<>(); //  <subTaskId, task_thread>
    private static SynchronizedHashMap<Integer, String> clientMap = new SynchronizedHashMap<>(); // <task_id, client_id>
    private static AtomicInteger taskIdCounter = new AtomicInteger();


    public static void main(String[] args) throws IOException {
//      args: ip port
        String host;
        int port;
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            host = "localhost";
            port = 7999;
        }
        ServerSocket socket = new ServerSocket(port, 0, InetAddress.getByName(host));
        while (true) {
            new Server(socket.accept());
        }
    }

    private Socket socket;

    private Server(Socket socket) {
        this.socket = socket;
        start();
    }

    @Override
    public void run() {
//        Основной поток сервера, при работе с запросом
//        Для каждого запроса из поступившего ServerRequest запускается отдельный поток-обработчик
//        Когда все обработчики завершают свою работу, сокет закрывается
        HashSet<MessageThread> startedThreads = new HashSet<>();
        try {
            ServerRequest request = getServerRequest();
            String clientId = request.getClientId();
            long requestId = request.getRequestId();
            if (request.hasSubmit()) {
                startedThreads.add(new TaskThread(socket, request.getSubmit().getTask(), requestId, clientId));
            }
            if (request.hasSubscribe()) {
                startedThreads.add(new SubscribeThread(socket, requestId, request.getSubscribe()));
            }
            if (request.hasList()) {
                startedThreads.add(new ListThread(socket, requestId));
            }
//            Ожидание завершения работы обработчиков
            for (MessageThread thread: startedThreads)
                thread.join();
//            Закрытие сокета
            socket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ServerRequest getServerRequest() throws IOException {
//        Читает запрос с сокета и возращает его
        WrapperMessage wrapperMessage = WrapperMessage.parseDelimitedFrom(socket.getInputStream());
        if (!wrapperMessage.hasRequest()) {
            throw new IOException("Message doesn't contains Request");
        }
        return wrapperMessage.getRequest();
    }

    abstract class MessageThread extends Thread {
//        Базовый класс всех обработчиков
        private Socket socket;
        private long requestId;

        MessageThread(Socket socket, long requestId) {
            this.socket = socket;
            this.requestId = requestId;
        }

        void sendResponse(GeneratedMessage message) {
            ServerResponse.Builder builder = ServerResponse.newBuilder();
            builder.setRequestId(requestId);
            if (message instanceof SubmitTaskResponse) builder.setSubmitResponse((SubmitTaskResponse) message);
            if (message instanceof SubscribeResponse) builder.setSubscribeResponse((SubscribeResponse) message);
            if (message instanceof ListTasksResponse) builder.setListResponse((ListTasksResponse) message);
//            Отправляет на сокет сообщение response
            try {
                OutputStream out = socket.getOutputStream();
                WrapperMessage.Builder wrapperMessageBuilder = WrapperMessage.newBuilder();
                wrapperMessageBuilder.setResponse(builder);
                wrapperMessageBuilder.build().writeDelimitedTo(out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private class TaskThread extends MessageThread {
//        Обработчик задания
        private Task task; // Исходное задание
        private Status status;
        private long a, b, p, m, n; // Переменные для подсчета
        private final int taskId; // Уникальный идентефикатор

        TaskThread(Socket socket, Task task, long requestId, String clientId) throws InterruptedException {
            super(socket, requestId);
            this.status = Status.OK;
            this.task = task;
            this.taskId = taskIdCounter.incrementAndGet();
            clientMap.put(this.taskId, clientId);
            taskMap.put(this.taskId, this);
            start();
        }

        @Override
        public void run() {
            try {
                sendResponse();
                initializeVariables();
                if (status == Status.OK)
                    calculate();
            } catch (InterruptedException e) {
                status = Status.ERROR;
            }
        }

        private void initializeVariables() throws InterruptedException {
            if (task.getA().hasValue()) {
                this.a = task.getA().getValue();
            } else {
                Pair<Status, Long> res = taskMap.get(task.getA().getDependentTaskId()).getResult();
                if (res.getKey() == Status.ERROR)
                    status = Status.ERROR;
                else
                    this.a = res.getValue();
            }
            if (task.getB().hasValue()) {
                this.b = task.getB().getValue();
            } else {
                Pair<Status, Long> res = taskMap.get(task.getB().getDependentTaskId()).getResult();
                if (res.getKey() == Status.ERROR)
                    status = Status.ERROR;
                else
                    this.b = res.getValue();
            }
            if (task.getP().hasValue()) {
                this.p = task.getP().getValue();
            } else {
                Pair<Status, Long> res = taskMap.get(task.getP().getDependentTaskId()).getResult();
                if (res.getKey() == Status.ERROR)
                    status = Status.ERROR;
                else
                    this.p = res.getValue();
            }
            if (task.getM().hasValue()) {
                this.m = task.getM().getValue();
            } else {
                Pair<Status, Long> res = taskMap.get(task.getM().getDependentTaskId()).getResult();
                if (res.getKey() == Status.ERROR)
                    status = Status.ERROR;
                else
                    this.m = res.getValue();
            }
            this.n = task.getN();
        }

        private void calculate() {
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
        }

        void sendResponse() {
            SubmitTaskResponse.Builder builder = SubmitTaskResponse.newBuilder();
            builder.setSubmittedTaskId(this.taskId);
            builder.setStatus(this.status);
            SubmitTaskResponse response = builder.build();
            sendResponse(response);
        }

//         don't call from self methods!!!!
        Pair<Status, Long> getResult() {
//            Возвращает пару (Статус, Результат)
            try {
                this.join();
                return new Pair<>(status, a);
            } catch (InterruptedException e) {
                return new Pair<>(Status.ERROR, null);
            }
        }

        Task getTask() {
            return task;
        }
    }

    private class SubscribeThread extends MessageThread {
        private int subTaskId;

        SubscribeThread(Socket socket, long requestId, Subscribe subscribe) {
            super(socket, requestId);
            this.subTaskId = subscribe.getTaskId();
            start();
        }

        @Override
        public void run() {
            SubscribeResponse.Builder builder = SubscribeResponse.newBuilder();
            Pair<Status, Long> result;
            try {
                if (taskMap.containsKey(subTaskId))
                    result = taskMap.get(subTaskId).getResult();
                else
                    result = new Pair<>(Status.ERROR, null);
            } catch (InterruptedException e) {
                e.printStackTrace();
                result = new Pair<>(Status.ERROR, null);
            }
            builder.setStatus(result.getKey());
            if (result.getKey() == Status.OK)
                builder.setValue(result.getValue());
            SubscribeResponse response = builder.build();

            sendResponse(response);
        }

    }

    private class ListThread extends MessageThread {
        ListThread(Socket socket, long requestId) {
            super(socket, requestId);
            start();
        }

        @Override
        public void run() {
            ListTasksResponse.Builder builder = ListTasksResponse.newBuilder();
            builder.setStatus(Status.OK);
            builder.addAllTasks(getTasks());
            ListTasksResponse response = builder.build();
            sendResponse(response);
        }

        private List<Protocol.ListTasksResponse.TaskDescription> getTasks() {
            ArrayList<Protocol.ListTasksResponse.TaskDescription> res = new ArrayList<>();
            for (Integer taskId: taskMap.keySet()) {
                try {
                    TaskThread task = taskMap.get(taskId);
                    ListTasksResponse.TaskDescription.Builder builder =
                            ListTasksResponse.TaskDescription.newBuilder();
                    builder.setTaskId(taskId);
                    builder.setClientId(clientMap.get(taskId));
                    builder.setTask(task.getTask());
                    if (!task.isAlive()) {
                        Pair<Status, Long> taskResult = task.getResult();
                        if (taskResult.getKey() == Status.OK)
                            builder.setResult(taskResult.getValue());
                    }
                    res.add(builder.build());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return res;
        }
    }
}