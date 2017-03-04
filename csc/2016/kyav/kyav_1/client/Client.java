package client;

import utils.Task;
import utils.TaskParameter;
import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/*
*   Тестовая реализация клиента
*/

public class Client extends Thread{
    private Socket clientSocket;
    private String clientID;
    private long requestId;

    public Client(String serverIP, int serverPort, String clientID) throws IOException {
        clientSocket = new Socket(serverIP, serverPort);
        this.clientID = clientID;
        this.requestId = 0;
    }

    @Override
    public void run() {
        // поток для чтения данных от сервера
        new Thread(){
            @Override
            public void run() {
                try {
                    //int size = 0;
                    while (true) {
//                        size = clientSocket.getInputStream().read();
//                        byte buf[] = new byte[size];
//                        clientSocket.getInputStream().read(buf);
//                        Protocol.ServerResponse response = Protocol.ServerResponse.parseFrom(buf);
                        Protocol.ServerResponse response = Protocol.ServerResponse.parseDelimitedFrom(clientSocket.getInputStream());
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    System.out.println("Error with server connection " + clientSocket.getInetAddress());
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (IOException e) {}
                }
            }
        }.start();

        // тестовые запросы
        sendSubmitTaskRequest(1, 2, 3, 4, 5);
        sendSubmitTaskRequest(2, 3, 4, 5, 6);
        sendSubmitTaskRequest(505, 842, 666, 987, 1234);
        sendSubscribeTaskRequest(3);
        sendSubscribeTaskRequest(2);
        sendTaskListRequest();

    }

    private void send(Protocol.ServerRequest serverRequest){
        OutputStream out = null;
        try {
            out = clientSocket.getOutputStream();
            synchronized (out){
                out.write(serverRequest.getSerializedSize());
                serverRequest.writeTo(out);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("Problems with socket output stream");
            e.printStackTrace();
        }
    }

    /*  генерация запроса на запуск задачи */
    private void sendSubmitTaskRequest(int a, int b, int p, int m, int n) {
        TaskParameter aParam = new TaskParameter(a);
        TaskParameter bParam = new TaskParameter(b);
        TaskParameter pParam = new TaskParameter(p);
        TaskParameter mParam = new TaskParameter(m);
        TaskParameter nParam = new TaskParameter(n);
        Task currentTask = new Task(aParam, bParam, pParam, mParam, nParam, 0, clientID);

        Protocol.SubmitTask.Builder taskBuilder = Protocol.SubmitTask.newBuilder();
        taskBuilder.setTask(currentTask.toProtocolTask());

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubmit(taskBuilder.build());
        builder.setRequestId(requestId++);
        builder.setClientId(clientID);

        send(builder.build());
    }
    /*  генерация запроса на подписку на результат */
    private void sendSubscribeTaskRequest(int taskId){
        Protocol.Subscribe.Builder subscribeBuider = Protocol.Subscribe.newBuilder();
        subscribeBuider.setTaskId(taskId);

        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setSubscribe(subscribeBuider.build());
        builder.setRequestId(requestId++);
        builder.setClientId(clientID);

        send(builder.build());
    }

    /*  генерация запроса на просмотр списка выполняющихся задач*/
    private void sendTaskListRequest(){
        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setList(Protocol.ListTasks.newBuilder().build());
        builder.setRequestId(requestId++);
        builder.setClientId(clientID);
        send(builder.build());
    }

}
