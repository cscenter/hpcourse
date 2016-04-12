package server;

import com.google.protobuf.CodedOutputStream;
import communication.Protocol.*;
import com.google.protobuf.CodedInputStream;

import java.net.*;
import java.io.*;

class ServerThread extends Thread {
    private final Socket socket;
    private final TaskManager taskManager;

    ServerThread(Socket socket, TaskManager taskManager) {
        this.socket = socket;
        this.taskManager = taskManager;
    }

    @Override
    public void run() {
        try (
                OutputStream output = socket.getOutputStream();
                InputStream input = socket.getInputStream();
        ) {
            CodedInputStream codedInputStream = CodedInputStream.newInstance(input);
            int messageSize = codedInputStream.readRawVarint32();
            if (messageSize <= 0) {
                throw new IOException("messageSize <= 0");
            }

            byte[] data = new byte[messageSize];
            if (input.read(data) < messageSize) {
                throw new IOException("Failed to read message bytes");
            }

            ServerRequest request = ServerRequest.parseFrom(data);
            ServerResponse.Builder responseBuilder = ServerResponse.newBuilder();
            responseBuilder.setRequestId(request.getRequestId());

            if (request.hasSubmit()) {
                Task task = request.getSubmit().getTask();
                int id = taskManager.addTask(task);

                SubmitTaskResponse.Builder submitResponse = SubmitTaskResponse.newBuilder();
                submitResponse.setSubmittedTaskId(id).setStatus(Status.OK);
                responseBuilder.setSubmitResponse(submitResponse);
            } else if (request.hasSubscribe()) {
                Subscribe subscribe = request.getSubscribe();
                int id = subscribe.getTaskId();
                long result = taskManager.getResult(id);

                SubscribeResponse.Builder builder = SubscribeResponse.newBuilder();
                builder.setStatus(Status.OK).setValue(result);
                responseBuilder.setSubscribeResponse(builder);
            } else if (request.hasList()) {
                ListTasksResponse.Builder builder = ListTasksResponse.newBuilder();

                for (Integer id : taskManager.getRunningTasks()) {
                    ListTasksResponse.TaskDescription.Builder taskDescBuilder = ListTasksResponse.TaskDescription.newBuilder();
                    taskDescBuilder.setClientId(request.getClientId())
                            .setTaskId(id)
                            .setTask(taskManager.getTask(id));
                    builder.addTasks(taskDescBuilder);
                }

            } else {
                throw new IOException("Invalid request");
            }

            WrapperMessage message = WrapperMessage.newBuilder().setResponse(responseBuilder.build()).build();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(output);
            codedOutputStream.writeRawVarint32(message.getSerializedSize());
            message.writeTo(codedOutputStream);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}