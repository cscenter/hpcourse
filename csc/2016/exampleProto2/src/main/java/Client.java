/**
 * Created by Alex on 08.04.2016.
 */

import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

class Client extends Thread {
    public static void main(String args[]) throws InterruptedException, IOException {
        for (int i = 1; i <= 1; i++) {
            Thread.currentThread().sleep(10);
            Socket s = new Socket("localhost", 3129);
            Protocol.Task.Param param=Protocol.Task.Param.newBuilder().setValue(1).build();
            //Protocol.Task.Param param=Protocol.Task.Param.newBuilder().setDependentTaskId(1).build();
            Protocol.Task.Param param1=Protocol.Task.Param.newBuilder().setValue(1).build();
            Protocol.Task.Param param2=Protocol.Task.Param.newBuilder().setValue(1).build();
            Protocol.Task.Param param3=Protocol.Task.Param.newBuilder().setValue(1).build();
            Protocol.Task.Param param4=Protocol.Task.Param.newBuilder().setValue(1).build();
            Protocol.Task.Param param5=Protocol.Task.Param.newBuilder().setDependentTaskId(1).build();
            Protocol.Subscribe subscribe=Protocol.Subscribe.newBuilder().setTaskId(i).build();
            Protocol.Task task=Protocol.Task.newBuilder().setA(param).setB(param1).setM(param2).
                    setN(i).setP(param4).build();
            Protocol.SubmitTask submitTask=Protocol.SubmitTask.newBuilder().setTask(task).build();
            Protocol.ServerRequest serverRequest= Protocol.
                    ServerRequest.newBuilder().setClientId("FIRST").
                    setRequestId(1).setSubmit(submitTask).setSubscribe(subscribe).build();
            Protocol.WrapperMessage wrapperMessage=Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build();
            wrapperMessage.writeDelimitedTo(s.getOutputStream());
            Protocol.WrapperMessage message = Protocol.WrapperMessage.parseDelimitedFrom(s.getInputStream());
            System.out.println(message);
        }
    }
}