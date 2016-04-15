import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Optional;
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
                long result = new SubmitTaskExecutor(taskId).startSubmitTask(task);
                synchronized (task) {
                    RequestsHistory.getTaskDescriptionById(taskId)
                            .setResult(Optional.of(result))
                            .setStatus(Optional.of(Protocol.Status.OK))
                            .setDone();
                    task.notifyAll();
                }
                response = buildSubmitResponse(request);
            } else if (request.hasSubscribe()) {
                LOG.info("Subscribe request");
                int targetTaskId = request.getSubscribe().getTaskId();
                new SubscribeTaskExecutor(targetTaskId).startSubscribeTask();
                response = buildSubscribeResponse(request, targetTaskId);
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
        System.out.println(response.getSerializedSize());
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

    private Protocol.ServerResponse buildSubscribeResponse(Protocol.ServerRequest request, int id) {
        return Protocol.ServerResponse.newBuilder()
                .setSubscribeResponse(Protocol.SubscribeResponse.newBuilder()
                .setStatus(RequestsHistory.getTaskDescriptionById(id).getStatus())
                .setValue(RequestsHistory.getTaskDescriptionById(id).getResult()))
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
