package clients;

import com.sun.corba.se.spi.activation.Server;
import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;

public class SubmitClient {

    private static final Logger LOG = Logger.getLogger("Client");
    private static final int PORT = 33333;

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", PORT)) {
            makeRequest(socket);
        } catch (IOException e) {
            LOG.warning("Connection error");
            e.printStackTrace();
        }
    }


    public static void makeRequest(Socket socket) {
        Protocol.SubmitTask submitTask = buildSubmitTask();
        Protocol.ServerRequest.Builder builder = Protocol.ServerRequest.newBuilder();
        builder.setRequestId(1).setClientId("user").setSubmit(submitTask);
        Protocol.ServerRequest request = builder.build();
        try (OutputStream outputStream = socket.getOutputStream();
             InputStream inputStream = socket.getInputStream()) {
            request.writeDelimitedTo(outputStream);
            Protocol.ServerResponse response = Protocol.ServerResponse.parseDelimitedFrom(inputStream);
            System.out.println(response.getSubmitResponse().getStatus());
        } catch (IOException e) {
            LOG.warning("Send request error");
            e.printStackTrace();
        }
    }

    public static Protocol.SubmitTask buildSubmitTask() {
        Protocol.Task.Builder builder = Protocol.Task.newBuilder();
        Protocol.Task.Param.Builder param = Protocol.Task.Param.newBuilder();

        param.clearParamValue();
        param.setValue(1);
        //param.setDependentTaskId(1);
        builder.setA(param.build());

        param.clearParamValue();
        param.setValue(2);
        //param.setDependentTaskId(2);
        builder.setB(param.build());

        param.clearParamValue();
        param.setValue(3);
        builder.setP(param.build());

        param.clearParamValue();
        param.setValue(0);
        builder.setM(param.build());

        param.clearParamValue();
        param.setValue(5);
        builder.setN(10000);

        return Protocol.SubmitTask.newBuilder().setTask(builder.build()).build();
    }

}
