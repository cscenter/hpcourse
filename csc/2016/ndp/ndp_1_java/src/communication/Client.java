package communication;

import com.google.protobuf.GeneratedMessage;

import java.io.IOException;
import java.net.Socket;

import static communication.Protocol.*;

// Простой клиент для тестов серевера
// Все параметры клиента выставляются вручную
public class Client extends Thread {

    private static String serverHost;
    private static int serverPort;

    // args: server_host server_port
    public static void main(String[] args) throws IOException{
        if (args.length >= 2) {
            serverHost = args[0];
            serverPort = Integer.parseInt(args[1]);
        } else {
            serverHost = "localhost";
            serverPort = 15;
        }

        new Client("first", 10, "SubmitTask");
        new Client("first1", 16, "SubmitTask");
        new Client("first2", 15, "SubmitTask");
        new Client("first3", 17, "SubmitTask");
        new Client("first4", 18, "SubmitTask");
        new Client("second", 12, "Subscribe");
        new Client("third", 14, "ListTasks");
    }

    private long requestId;
    private String clientId, messageType;
    private Socket socket;

    public Client(String clientId, long requestId, String messageType) {
        this.clientId = clientId;
        this.requestId = requestId;
        this.messageType = messageType;
        start();
    }

    @Override
    public void run() {
        GeneratedMessage task = null;
        if (messageType.equals("SubmitTask")) task = makeSubmitTask();
        if (messageType.equals("Subscribe")) task = makeSubscribeTask(10);
        if (messageType.equals("ListTasks")) task = makeListTasks();
        sendServerRequest(makeServerRequest(task));
        ServerResponse response = getServerResponse();
//        Раскомментировать, если интересует результат ответов
//        printResponse(response);
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void printResponse(ServerResponse response) {
        System.out.println("------------------------------------");
        System.out.println("client_id " + clientId);
        System.out.println("request_id " + response.getRequestId());
        if (response.hasSubmitResponse()) {
            SubmitTaskResponse r = response.getSubmitResponse();
            System.out.println("Submit Response");
            System.out.println("status " + r.getStatus());
        }
        if (response.hasSubscribeResponse()) {
            System.out.println("Subscribe Response");
            SubscribeResponse r = response.getSubscribeResponse();
            System.out.println("status " + r.getStatus());
            if (r.hasValue())
                System.out.println("value " + r.getValue());
        }
        if (response.hasListResponse()) {
            System.out.println("List Response");
            ListTasksResponse r = response.getListResponse();
            r.getTasksList().forEach(this::printTaskDescription);
        }
    }

    private void printTaskDescription(ListTasksResponse.TaskDescription task) {
        System.out.println("Task " + task.getTaskId());
        System.out.println("Client " + task.getClientId());
        if (task.hasResult())
            System.out.println("Result " + task.getResult());
    }

    private ServerResponse getServerResponse() {
        try {
//            int size = socket.getInputStream().read();
//            System.out.println("Size = " + size);
//            byte buf[] = new byte[size];
//            socket.getInputStream().read(buf);
            return ServerResponse.parseDelimitedFrom(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void sendServerRequest(ServerRequest request) {
        try {
            socket = new Socket(serverHost, serverPort);
            request.writeDelimitedTo(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ServerRequest makeServerRequest(GeneratedMessage message) {
        ServerRequest.Builder builder = ServerRequest.newBuilder();
        builder.setRequestId(requestId);
        builder.setClientId(clientId);
        if (message instanceof SubmitTask) builder.setSubmit((SubmitTask)message);
        if (message instanceof Subscribe) builder.setSubscribe((Subscribe)message);
        if (message instanceof ListTasks) builder.setList((ListTasks)message);
        return builder.build();
    }

    private static SubmitTask makeSubmitTask() {
        Task.Builder taskBuilder = Task.newBuilder();
        Task.Param.Builder paramBuilder = Task.Param.newBuilder();

        paramBuilder.clearParamValue();
        paramBuilder.setValue(10);
        taskBuilder.setA(paramBuilder.build());
        paramBuilder.clearParamValue();
        paramBuilder.setValue(3);
        taskBuilder.setB(paramBuilder.build());
        paramBuilder.clearParamValue();
        paramBuilder.setValue(7);
        taskBuilder.setP(paramBuilder.build());
        paramBuilder.clearParamValue();
        paramBuilder.setValue(10);
        taskBuilder.setM(paramBuilder.build());

        taskBuilder.setN(10);

        SubmitTask.Builder stBuilder = SubmitTask.newBuilder();
        stBuilder.setTask(taskBuilder.build());
        return stBuilder.build();
    }

    private static Subscribe makeSubscribeTask(int taskId) {
        Subscribe.Builder builder = Subscribe.newBuilder();
        builder.setTaskId(taskId);
        return builder.build();
    }

    private static ListTasks makeListTasks() {
        return ListTasks.newBuilder().build();
    }


}
