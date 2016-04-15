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
        try {
            Protocol.ServerResponse.Builder responseBuilder = Protocol.ServerResponse.newBuilder();
            responseBuilder.setRequestId(request.getRequestId());

            if (request.hasSubmit()) {
                responseBuilder.setSubmitResponse(server.submitTask(request.getSubmit(), request.getClientId()));
            }
            if (request.hasSubscribe()) {
                responseBuilder.setSubscribeResponse(server.subscribe(request.getSubscribe()));
            }
            if (request.hasList()) {
                responseBuilder.setListResponse(server.listTasks(request.getList()));
            }
            synchronized (outputStream) {
                responseBuilder.build().writeTo(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
