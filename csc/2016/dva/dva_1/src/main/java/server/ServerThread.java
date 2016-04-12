package server;

import com.google.protobuf.CodedOutputStream;
import communication.Protocol.*;
import com.google.protobuf.CodedInputStream;

import java.net.*;
import java.io.*;
import java.util.concurrent.Callable;

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

            WrapperMessage inputMessage = WrapperMessage.parseFrom(data);
            if (!inputMessage.hasRequest())
                throw new IOException("Message does not contain request");

            ServerRequest request = inputMessage.getRequest();
            ServerResponse response = new RequestHandler(request, taskManager).call();

            WrapperMessage message = WrapperMessage.newBuilder().setResponse(response).build();
            CodedOutputStream codedOutputStream = CodedOutputStream.newInstance(output);
            codedOutputStream.writeRawVarint32(message.getSerializedSize());
            message.writeTo(codedOutputStream);
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}