package simpleClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import communication.Protocol.*;

/**
 * Created by andrey on 07.04.16.
 */

public class Client extends Thread {

    private String client_id;
    private Socket socket;
    private int requestID = 0;

    public static void main(String[] args) {
        try {
            startClient();
        } catch (IOException e) {
            e.getMessage();
        }
    }

    Client(String client_id, Socket socket) {
        this.client_id = client_id;
        this.socket = socket;
        start();
    }

    public static void startClient() throws IOException {
        for (int i = 0; i < 1; i++) {
            new Client("Client" + i, new Socket("localhost", 4242));
        }
    }

    @Override
    public void run() {
        try {
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();

            while(true) {

                ServerRequest request = getSubmitTaskRequest(requestID++, 1, 0, -1, 0, 0, null); // посылаем запрос на выполнение
//                os.write(serializeRequest(request));
                WrapperMessage message = WrapperMessage.newBuilder().setRequest(request).build();
                message.writeDelimitedTo(os);
                os.flush();

                request = getTaskSubscribeResquest(requestID++, 0); // посылаем запрос на подписку
                message = WrapperMessage.newBuilder().setRequest(request).build();
                message.writeDelimitedTo(os);
                os.flush();


                for (int i = 0; i < 10; i++) {
                    request = getSubmitTaskRequest(requestID++, 1, 2, 0, 3, 1_000_000, i);
                    message = WrapperMessage.newBuilder().setRequest(request).build();
                    message.writeDelimitedTo(os);
                    os.flush();

                    request = getTaskSubscribeResquest(requestID++, i + 1); // посылаем запрос на подписку
                    message = WrapperMessage.newBuilder().setRequest(request).build();
                    message.writeDelimitedTo(os);
                    os.flush();

                }

                request = getListTasksRequest(); // посылаем запрос на все задачи
                message = WrapperMessage.newBuilder().setRequest(request).build();
                message.writeDelimitedTo(os);
                os.flush();


                for (int iter = 0; iter < 23; iter++) {
                    ServerResponse response = getResponse(is);
                    if (response.hasSubmitResponse()) {
                        SubmitTaskResponse r = response.getSubmitResponse();
                        System.out.println("Submit response: task id = " + r.getSubmittedTaskId() + " Run with status = " + r.getStatus());
                    }
                    if (response.hasSubscribeResponse()) {
                        SubscribeResponse r = response.getSubscribeResponse();
                        System.out.println("Subscribe response: " + r.getStatus() + " result: " + r.getValue());
                    }
                    if (response.hasListResponse()) {
                        ListTasksResponse l = response.getListResponse();
                        System.out.println("LIst response:");
                        for (int i = 0; i < l.getTasksCount(); i++) {
                            ListTasksResponse.TaskDescription task = l.getTasks(i);
                            System.out.println("---> Task id: " + task.getTaskId() + " " + task.getClientId() + " Result " + task.getResult());
                        }
                    }
                }
                os.close();
                break;
            }
        }
        catch (IOException e) {
            System.out.println("Output stream error");
            e.printStackTrace();
        }
    }

    private ServerResponse getResponse(InputStream is) throws IOException{
        return WrapperMessage.parseDelimitedFrom(is).getResponse();
    }

    private ServerRequest getSubmitTaskRequest(int requestID, long a, long b, long p , long m, long n, Integer depententTaskID) {
        Task.Param param = null;
        if (depententTaskID != null) {
            param = Task.Param.newBuilder().setDependentTaskId(depententTaskID).build();
        }
        else {
            param = Task.Param.newBuilder().setValue(a).build();
        }

        Task sendTask = Task
                .newBuilder()
                .setA(param)
                .setB(Task.Param.newBuilder().setValue(b).build())
                .setP(Task.Param.newBuilder().setDependentTaskId((int)p).build()) //setP(Task.Param.newBuilder().setValue(p).build())
                .setM(Task.Param.newBuilder().setValue(m).build())
                .setN(n).build();

        ServerRequest request = ServerRequest.newBuilder()
                .setClientId(client_id)
                .setRequestId(requestID)
                .setSubmit(SubmitTask.newBuilder().setTask(sendTask))
                .build();

        return request;
    }

    private ServerRequest getTaskSubscribeResquest(int requestID, int taskID) {
        return ServerRequest.newBuilder()
                .setClientId(client_id)
                .setRequestId(requestID)
                .setSubscribe(Subscribe.newBuilder().setTaskId(taskID).build())
                .build();
    }

    private ServerRequest getListTasksRequest() {
        return ServerRequest.newBuilder()
                .setClientId(client_id)
                .setRequestId(requestID)
                .setList(ListTasks.newBuilder().build())
                .build();
    }

}
