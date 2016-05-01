import java.io.*;
import java.net.*;
import java.util.List;

import com.google.protobuf.GeneratedMessage;
import communication.Protocol.*;

public class Client {

    static final int PORT_NUMBER = 44444;

    public static void main(String[] args) throws InterruptedException {
//        for (int i = 0; i < 3; i++) {
////            Thread.sleep(1000);
//            new TaskClient(i + " first");
//            new TaskClient(i + " second");
//            new TaskClient(i + " third");
//        }
        new TaskClient("Third", 1, "Submit");
//        new TaskClient("Third-2", 2, "Submit");
//        Thread.sleep(200);
        new TaskClient("Second", 3, "Subscribe");
//        new TaskClient("Fourth", 4, "List");
    }
    ///dddddddddddddd
}

class TaskClient extends Thread {

    String clientId;
    long requestId;
    Socket socket;
    String task;

    TaskClient(String clientId, long requestId, String task) {
        this.clientId = clientId;
        this.requestId = requestId;
        this.task = task;
        start();
    }

    public void run() {
        if (task.equals("Subscribe")) {
            sendMassageAndTakeResponse(makeServerRequest(makeSubscribeTask(1)));
        } else if (task.equals("Submit")) {
            sendMassageAndTakeResponse(makeServerRequest(makeSubmitTask()));
        } else {
            sendMassageAndTakeResponse(makeServerRequest(makeListTask()));
        }
//        sendMassageAndTakeResponse(makeServerRequest(makeSubscribeTask(6)));
    }

    void sendMassageAndTakeResponse(ServerRequest request) {
        try {
            socket = new Socket("localhost", Client.PORT_NUMBER);

            //Пишем
            SendMessageToServer(request);
            System.out.println("<<<  " + task + "  >>>");
            // читаем ответ
            ServerResponse serverResponse = getServerResponse();
            System.out.println("<<<  " + "take answer" + "  >>>");
            printResponse(serverResponse);
        } catch (Exception e) {
            System.out.println("work client error: ");
            e.printStackTrace();
            System.out.println();
        }
    }

    void printResponse(ServerResponse serverResponse) {
        long id = serverResponse.getRequestId();
        System.out.println("id: " + id);
        if (serverResponse.hasSubmitResponse()) {
            SubmitTaskResponse submitTaskResponse = serverResponse.getSubmitResponse();
            System.out.println("submittedTaskId: " + submitTaskResponse.getSubmittedTaskId());
            System.out.println("status: " + submitTaskResponse.getStatus().name());
            System.out.println();
        } else if (serverResponse.hasSubscribeResponse()) {
            SubscribeResponse subscribeResponse = serverResponse.getSubscribeResponse();
            System.out.println("status: " + subscribeResponse.getStatus().name());
            if (subscribeResponse.hasValue())
                System.out.println("value: " + subscribeResponse.getValue());
            System.out.println();
        } else if (serverResponse.hasListResponse()) {
            ListTasksResponse listTasksResponse = serverResponse.getListResponse();
            List<ListTasksResponse.TaskDescription> listOfTasks = listTasksResponse.getTasksList();
            System.out.println("Status:" + listTasksResponse.getStatus().name());
            for (ListTasksResponse.TaskDescription task : listOfTasks) {
                printTaskDescriptions(task);
            }
        }
    }

    void printTaskDescriptions(ListTasksResponse.TaskDescription task) {
        System.out.println("taskId: " + task.getTaskId());
        System.out.println("clientId: " + task.getClientId());
        if (task.hasResult()) {
            System.out.println("Result: " + task.getResult());
        }
        Task curTask = task.getTask();
        System.out.println("a: " + getParam(curTask.getA()));
        System.out.println("b: " + getParam(curTask.getB()));
        System.out.println("p: " + getParam(curTask.getP()));
        System.out.println("m: " + getParam(curTask.getM()));
        System.out.println("n: " + curTask.getN());
    }

    long getParam(Task.Param param) {
        if (param.hasValue())
            return param.getValue();
        return param.getDependentTaskId();
    }

    void SendMessageToServer(ServerRequest request) throws IOException {
        request.writeDelimitedTo(socket.getOutputStream());
    }

    ServerResponse getServerResponse() throws IOException {
        return ServerResponse.parseDelimitedFrom(socket.getInputStream());
    }

    //all tasks extend com.google.protobuf.GeneratedMessage
    public ServerRequest makeServerRequest(GeneratedMessage message) {
        ServerRequest.Builder request = ServerRequest.newBuilder().setRequestId(requestId).setClientId(clientId);
        if (message instanceof SubmitTask) request.setSubmit((SubmitTask) message);
        if (message instanceof Subscribe) request.setSubscribe((Subscribe) message);
        if (message instanceof ListTasks) request.setList((ListTasks) message);
        return request.build();
    }

    SubmitTask makeSubmitTask() {
        SubmitTask.Builder submitTask = SubmitTask.newBuilder();
        Task.Builder task = Task.newBuilder();
        Task.Param.Builder param = Task.Param.newBuilder();

        //параметр a
        param.clearParamValue();
        param.setValue(3);
        task.setA(param.build());

        //параметр b
        param.clearParamValue();
        param.setValue(150);
        task.setB(param.build());

        //параметр p
        param.clearParamValue();
        param.setValue(24);
        task.setP(param.build());

        //параметр m
        param.clearParamValue();
        param.setValue(24);
//        param.setDependentTaskId(1);
        task.setM(param.build());

        //параметр n
        task.setN(2_000_000_000l);

        return submitTask.setTask(task.build()).build();
    }

    Subscribe makeSubscribeTask(int idTask) {
        return Subscribe.newBuilder().setTaskId(idTask).build();
    }

    ListTasks makeListTask() {
        ListTasks.Builder listTask = ListTasks.newBuilder();
        return listTask.build();
    }

}
