package client;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class Client {

    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

    private final Socket socket;
    private final String clientId;
    private final AtomicInteger requestId = new AtomicInteger(0);

    public Client(final String host, final int port, final String clientId) throws IOException {
        socket = new Socket(host, port);
        this.clientId = clientId;
    }

    /**
     * @param serverRequest
     * @throws IOException occur when problem with sending task to server
     */
    public void sendTask(final Protocol.ServerRequest serverRequest) throws IOException {
        LOGGER.info("Client try to send server request:" + serverRequest.getClientId() + " " + serverRequest.getRequestId());
        final OutputStream outputStream = socket.getOutputStream();
        synchronized (socket) {
            final Protocol.WrapperMessage message = Protocol.WrapperMessage.newBuilder().setRequest(serverRequest).build();
            outputStream.write(message.getSerializedSize());
            message.writeTo(outputStream);
            outputStream.flush();
        }
    }

    public void submitTask(final Protocol.Task.Param a, final Protocol.Task.Param b, final Protocol.Task.Param p,
                           final Protocol.Task.Param m, final long n) throws IOException {
        final Protocol.Task task = Protocol.Task.newBuilder().setA(a).setB(b).setP(p).setM(m).setN(n).build();
        final Protocol.SubmitTask submitTask = Protocol.SubmitTask.newBuilder().setTask(task).build();
        sendTask(Protocol.ServerRequest.newBuilder().setRequestId(requestId.getAndIncrement()).setClientId(clientId).setSubmit(submitTask).build());
    }


}
