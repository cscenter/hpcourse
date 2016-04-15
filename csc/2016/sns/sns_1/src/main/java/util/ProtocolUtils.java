package util;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class ProtocolUtils {

    private static final Logger LOGGER = Logger.getLogger(ProtocolUtils.class.getName());

    public static Protocol.SubmitTaskResponse createSubmitTaskResponse(final int taskId, final Protocol.Status status) {
        return Protocol.SubmitTaskResponse.newBuilder()
                .setSubmittedTaskId(taskId)
                .setStatus(status)
                .build();
    }

    public static Protocol.ServerResponse.Builder createServerResponse(final Protocol.ServerRequest request) {
        return Protocol.ServerResponse.newBuilder()
                .setRequestId(request.getRequestId());
    }


    /**
     * @param socket  destination of message
     * @param message is message
     * @throws IOException when can't get output stream for socket
     */
    public static void sendWrappedMessage(final Socket socket, final Protocol.WrapperMessage message) throws IOException {
        LOGGER.info("Send wrapped message");
        final OutputStream outputStream = socket.getOutputStream();
        message.writeDelimitedTo(outputStream);
        LOGGER.info("Sent wrapped message");
    }

    /**
     * @param socket destination of message
     * @throws IOException when can't get input stream for socket
     */
    public static Protocol.WrapperMessage readWrappedMessage(final Socket socket) throws IOException, InterruptedException {
        final InputStream inputStream = socket.getInputStream();
        while (true) {
            LOGGER.info("Try read wrapped message");
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (inputStream.available() > 0) {
                synchronized (socket) {
                    if (inputStream.available() > 0) {
                        LOGGER.info("Read wrapped message");
                        return Protocol.WrapperMessage.parseDelimitedFrom(inputStream);
                    }
                }
            }
        }
    }

    public static Protocol.Task createTask(final Protocol.Task.Param a, final Protocol.Task.Param b, final Protocol.Task.Param p,
                                           final Protocol.Task.Param m, final long n) {
        return Protocol.Task.newBuilder().setA(a).setB(b).setP(p).setM(m).setN(n).build();
    }

    public static Protocol.SubmitTask createSubmitTask(final Protocol.Task task) {
        return Protocol.SubmitTask.newBuilder().setTask(task).build();
    }

    public static Protocol.WrapperMessage wrapRequest(final Protocol.ServerRequest request) {
        return Protocol.WrapperMessage.newBuilder().setRequest(request).build();
    }

    public static Protocol.WrapperMessage wrapResponse(final Protocol.ServerResponse response) {
        return Protocol.WrapperMessage.newBuilder().setResponse(response).build();
    }

    /**
     * One of the parameters must be null, not both
     *
     * @param value
     * @param dependentTaskId
     * @return {@code Param} if one of the parameters are not null, {@code null} if both are null
     */
    public static Protocol.Task.Param createParam(final Long value, final Integer dependentTaskId) {
        if (dependentTaskId != null && value != null) {
            return null;
        }

        if (dependentTaskId != null) {
            return Protocol.Task.Param.newBuilder().setDependentTaskId(dependentTaskId).build();
        }

        if (value != null) {
            return Protocol.Task.Param.newBuilder().setValue(value).build();
        }

        return null;
    }
}
