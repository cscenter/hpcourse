import communication.Protocol;
import utils.RequestsHistory;
import utils.SubmitTaskExecutor;
import utils.SubscribeTaskExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;

public class TaskThread extends Thread {

    private static final Logger LOG = Logger.getLogger("TaskThread");
    private Socket socket;
    private int taskId;

    public TaskThread(final Socket socket, int taskId) {
        this.socket = socket;
        this.taskId = taskId;
    }

    @Override
    public void run() {
        try (InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream()) {
            Protocol.ServerResponse response;
            Protocol.ServerRequest request = getServerRequest(inputStream);
            if (request.hasSubmit()) {
                LOG.info("Submit request");
                Protocol.Task task = request.getSubmit().getTask();
                RequestsHistory.putTask(taskId, request.getClientId(), task);
                long result = 0;
                Protocol.Status status = Protocol.Status.OK;
                try {
                    result = new SubmitTaskExecutor().startSubmitTask(task);
                } catch (IllegalArgumentException e) {
                    status = Protocol.Status.ERROR;
                }
                synchronized (task) {
                    RequestsHistory.getTaskDescriptionById(taskId)
                            .setResult(result)
                            .setStatus(status)
                            .setDone();
                    task.notifyAll();
                }
                response = buildSubmitResponse(request);
            } else if (request.hasSubscribe()) {
                LOG.info("Subscribe request");
                int targetTaskId = request.getSubscribe().getTaskId();
                Protocol.Status status = Protocol.Status.OK;
                long result = 0;
                try {
                    result = new SubscribeTaskExecutor(targetTaskId).startSubscribeTask();
                } catch (IllegalArgumentException e) {
                    status = Protocol.Status.ERROR;
                }
                response = buildSubscribeResponse(request, status, result);
            } else {
                LOG.info("List request");
                response = buildListResponse(request);
            }
            sendResponse(outputStream, response);
        } catch (IOException e) {
            LOG.warning("IO error");
            e.printStackTrace();
        }
    }

    private void sendResponse(OutputStream outputStream, Protocol.ServerResponse response) throws IOException {
        outputStream.write(response.getSerializedSize());
        response.writeTo(outputStream);
    }

    private Protocol.ServerResponse buildSubmitResponse(Protocol.ServerRequest request) {
        return Protocol.ServerResponse.newBuilder()
                .setSubmitResponse(Protocol.SubmitTaskResponse.newBuilder()
                .setStatus(RequestsHistory.getTaskDescriptionById(taskId).getStatus())
                .setSubmittedTaskId(taskId))
                .setRequestId(request.getRequestId())
                .build();
    }

    private Protocol.ServerResponse buildSubscribeResponse(Protocol.ServerRequest request, Protocol.Status status,
                                                           long result) {
        return Protocol.ServerResponse.newBuilder()
                .setSubscribeResponse(Protocol.SubscribeResponse.newBuilder()
                .setStatus(status)
                .setValue(result))
                .setRequestId(request.getRequestId())
                .build();
    }

    private Protocol.ServerResponse buildListResponse(Protocol.ServerRequest request) {
        return Protocol.ServerResponse.newBuilder()
                .setListResponse(Protocol.ListTasksResponse.newBuilder()
                .addAllTasks(RequestsHistory.getTasks()))
                .build();
    }

    private Protocol.ServerRequest getServerRequest(final InputStream inputStream) throws IOException {
            int size = inputStream.read();
            byte[] buf = new byte[size];
            inputStream.read(buf);
            return Protocol.ServerRequest.parseFrom(buf);
    }

}
