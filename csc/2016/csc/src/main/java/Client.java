import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

public class Client extends Thread{
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 1000);
        Protocol.WrapperMessage.Builder wrapperMessageBuilder = Protocol.WrapperMessage.newBuilder();
        Protocol.ServerRequest.Builder requestBuilder = Protocol.ServerRequest.newBuilder();
        requestBuilder.setClientId("client");


        //Для решения задачи

        /*
        requestBuilder.setRequestId(1);
        Protocol.Task.Builder taskBuilder = Protocol.Task.newBuilder();

        taskBuilder.setA(Protocol.Task.Param.newBuilder().setValue(1).build());
        taskBuilder.setB(Protocol.Task.Param.newBuilder().setValue(2).build());
        taskBuilder.setM(Protocol.Task.Param.newBuilder().setValue(3).build());
        taskBuilder.setP(Protocol.Task.Param.newBuilder().setValue(4).build());
        taskBuilder.setN(5);

        requestBuilder.setSubmit(Protocol.SubmitTask
                .newBuilder()
                .setTask(taskBuilder.build())
                .build());
        */

        //Для запросы всех задач

        /*
        requestBuilder.setRequestId(2);
        requestBuilder.setList(Protocol.ListTasks.newBuilder().build());
        */

        //Для подписки на задачу

        /*
        requestBuilder.setRequestId(3);
        requestBuilder.setSubscribe(Protocol.Subscribe.newBuilder().setTaskId(1).build());
        */


        Protocol.WrapperMessage message = wrapperMessageBuilder.setRequest(requestBuilder.build()).build();
        message.writeDelimitedTo(socket.getOutputStream());

        //Ответ сервера
        Protocol.WrapperMessage messageBack = Protocol.WrapperMessage.parseDelimitedFrom(socket.getInputStream());
        System.out.println(messageBack.toString());
    }
}
