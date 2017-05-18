package ru.csc.roman_fedorov;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import communication.Protocol;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Created by roman on 27.04.2016.
 */

public class RandomClient {
    private static Random randomGenerator = new Random();

    private static void sendRequest(Protocol.ServerRequest request, Socket socket) throws IOException {
        CodedOutputStream outputStream = CodedOutputStream.newInstance(socket.getOutputStream());
        outputStream.writeRawVarint32(request.getSerializedSize());
        outputStream.flush();
        request.writeTo(outputStream);
        outputStream.flush();
    }

    private static Protocol.Task.Param getRandomParam() {
        Protocol.Task.Param.Builder paramBuilder = Protocol.Task.Param.newBuilder();
        if (randomGenerator.nextInt(100) > 80) {
            return paramBuilder.setDependentTaskId(Server.idCounter.get()).build();
        } else {
            return paramBuilder.setValue(randomGenerator.nextInt(1_000_000_000)).build();
        }
    }

    public static void main(String[] args) throws IOException {
        String hostName = args.length == 2 ? args[0] : "localhost";
        int portNumber = args.length == 2 ? Integer.parseInt(args[1]) : Server.DEFAULT_PORT;
        try (Socket socket = new Socket(hostName, portNumber)) {
            Protocol.ServerRequest.Builder serverRequestBuilder = Protocol.ServerRequest.newBuilder();
            Protocol.ServerRequest.Builder requestBuilder = serverRequestBuilder
                    .setClientId(UUID.randomUUID().toString())
                    .setRequestId(randomGenerator.nextInt(1000000));
            int randomNumber = randomGenerator.nextInt();
            CodedInputStream inputStream = CodedInputStream.newInstance(socket.getInputStream());
            StringBuilder sb = new StringBuilder().append("=====||");
            if (randomNumber % 3 == 0) {
                sb.append("Submit new task||");
                Protocol.SubmitTask.Builder submitTaskBuilder = Protocol.SubmitTask.newBuilder();
                Protocol.Task.Builder taskBuilder = Protocol.Task.newBuilder();

                Protocol.ServerRequest request = requestBuilder
                        .setSubmit(
                                submitTaskBuilder
                                        .setTask(
                                                taskBuilder
                                                        .setA(getRandomParam())
                                                        .setB(getRandomParam())
                                                        .setP(getRandomParam())
                                                        .setM(getRandomParam())
                                                        .setN(randomGenerator.nextInt(1_000_000_000))
                                                        .build()
                                        ).build()
                        ).build();

                sendRequest(request, socket);

                int messageLength = inputStream.readRawVarint32();
                Protocol.SubmitTaskResponse response =
                        Protocol.SubmitTaskResponse.parseFrom(inputStream.readRawBytes(messageLength));
                sb.append(response.getStatus().getValueDescriptor().toString()).append("||")
                        .append(response.getSubmittedTaskId()).append("||");
            } else if (randomNumber % 3 == 1) {
                int taskId = randomGenerator.nextInt(10);
                sb.append("Subscribe to task: ").append(taskId).append("||");
                Protocol.ServerRequest request = requestBuilder
                        .setSubscribe(
                                Protocol.Subscribe.newBuilder().setTaskId(taskId).build()
                        ).build();

                sendRequest(request, socket);

                int messageLength = inputStream.readRawVarint32();
                Protocol.SubscribeResponse response =
                        Protocol.SubscribeResponse.parseFrom(inputStream.readRawBytes(messageLength));
                sb.append(response.getStatus().getValueDescriptor().toString()).append("\n")
                        .append(response.getValue()).append("\n");
            } else {
                sb.append("List tasks||");

                Protocol.ServerRequest request = requestBuilder.setList(Protocol.ListTasks.newBuilder()).build();
                sendRequest(request, socket);

                int messageLength = inputStream.readRawVarint32();
                Protocol.ListTasksResponse response =
                        Protocol.ListTasksResponse.parseFrom(inputStream.readRawBytes(messageLength));
                List<Protocol.ListTasksResponse.TaskDescription> list = response.getTasksList();
                for (Protocol.ListTasksResponse.TaskDescription taskDescription : list) {
                    sb.append(taskDescription.getClientId()).append(" @ ")
                            .append(String.valueOf(taskDescription.getTaskId())).append(" @ ")
                            .append(taskDescription.hasResult() ? taskDescription.getResult() : "Not ready")
                            .append("||");
                }
            }
            sb.append("-----------");
            System.out.println(sb);
        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + hostName);
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + hostName);
            System.exit(1);
        }
    }
}
