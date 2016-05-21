package server;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public class RequestResolver implements Runnable {
    private final Protocol.ServerRequest request;
    private final OutputStream outputStream;
    private final Server server;
    public RequestResolver(Server server, Protocol.ServerRequest request, OutputStream outputStream) {
        this.request = request;
        this.outputStream = outputStream;
        this.server = server;
    }

    @Override
    public void run() {
        Protocol.ServerResponse.Builder responseBuilder = Protocol.ServerResponse.newBuilder();
        responseBuilder.setRequestId(request.getRequestId());

        if (request.hasSubmit()) {
            try {
                responseBuilder.setSubmitResponse(server.submitTask(request.getSubmit(), request.getClientId()));
            } catch (Exception e) {
                responseBuilder.setSubmitResponse(Protocol.SubmitTaskResponse.newBuilder()
                        .setStatus(Protocol.Status.ERROR).setSubmittedTaskId(-1).build());
            }
        } else if (request.hasSubscribe()) {
            try {
                responseBuilder.setSubscribeResponse(server.subscribe(request.getSubscribe()));
            } catch (Exception e) {
                responseBuilder.setSubscribeResponse(Protocol.SubscribeResponse.newBuilder()
                        .setStatus(Protocol.Status.ERROR).build());
            }
        } else if (request.hasList()) {
            try {
                responseBuilder.setListResponse(server.listTasks(request.getList()));
            } catch (Exception e) {
                responseBuilder.setListResponse(Protocol.ListTasksResponse.newBuilder()
                        .setStatus(Protocol.Status.ERROR).build());
            }
        }

        try {
            synchronized (outputStream) {
                Protocol.WrapperMessage.newBuilder().setResponse(responseBuilder.build()).build()
                        .writeDelimitedTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            // Ignore because we couldn't send message to client. Trying again hardly help us
            System.err.println("Couldn't sent message to client");
        }
    }
}
