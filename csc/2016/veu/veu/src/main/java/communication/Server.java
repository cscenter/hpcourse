package communication;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static communication.Protocol.*;

public class Server {
  private final ServerSocket myServerSocket;
  private final Storage myStorage;

  public Server(ServerSocket serverSocket) {
    myServerSocket = serverSocket;
    myStorage = new Storage();
  }

  public static void main(String[] args) throws IOException {
    // ip port
    String ip = "localhost";
    int port = Integer.parseInt(args[0]);
    ServerSocket serverSocket = new ServerSocket(port);

    new Server(serverSocket).listen();
  }

  private void listen() {
    while (true) {
      try (
        Socket accept = myServerSocket.accept()) {
        new Processor(accept).start();
      } catch (IOException ignore) {}
    }
  }

  class Processor extends Thread {
    Socket mySocket;
    public Processor(Socket socket) {
      mySocket = socket;
    }

    @Override
    public void run() {
      try {
        ServerRequest serverRequest = Util.getRequest(mySocket);
        ServerResponse response = null;
        if (serverRequest.hasSubmit()) {
          int newId = TaskThread.registerTask(serverRequest.getSubmit(), myStorage);

          // todo fix
          SubmitTaskResponse submitTaskResponse = SubmitTaskResponse.newBuilder().setSubmittedTaskId(newId).build();
          response = ServerResponse.newBuilder().setSubmitResponse(submitTaskResponse).build();
        }
        if (serverRequest.hasSubscribe()) {
          Subscribe subscribe = serverRequest.getSubscribe();
          long result = myStorage.getValue(subscribe.getTaskId());
          SubscribeResponse subscribeResponse = SubscribeResponse.newBuilder().setValue(result).setStatus(Status.OK).build();
          response = ServerResponse.newBuilder().setSubscribeResponse(subscribeResponse).build();
        }
        if (serverRequest.hasList()) {
          ListTasksResponse listTasksResponse = ListTasksResponse.newBuilder().addAllTasks(myStorage.getTasks()).build();
          response = ServerResponse.newBuilder().setListResponse(listTasksResponse).build();
        }
        if (response == null) {
          throw new IllegalStateException("empty request");
        }
        int responseSize = response.getSerializedSize();
        mySocket.getOutputStream().write(responseSize);
        mySocket.getOutputStream().write(response.toByteArray());
      } catch (IOException e) {
        // todo implement
      }
    }
  }
}
