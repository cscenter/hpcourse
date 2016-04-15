package communication;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

import static communication.Protocol.*;

public class Server {
  private final static Logger log = Logger.getGlobal();

  private final ServerSocket myServerSocket;
  private final RequestProcessor[] myProcessors;

  public Server(ServerSocket serverSocket, RequestProcessor[] processors) {
    myServerSocket = serverSocket;
    myProcessors = processors;
  }

  public static void main(String[] args) throws IOException {
    // ip and port
//    String ip = "localhost";
    int port = Integer.parseInt(args[0]);
    final ServerSocket serverSocket = new ServerSocket(port);
    Storage storage = new Storage();
    RequestProcessor[] processors = {
      new TaskProcessor(storage),
      new ListProcessor(storage),
      new SubscribeProcessor(storage),
      new RequestProcessor() {
        @Nullable
        public ServerResponse processRequest(ServerRequest request) {
          System.out.println("warning: empty request");
          return ServerResponse.newBuilder().setRequestId(request.getRequestId()).build();
        }
      }
    };
    new Thread(new Runnable() {
      public void run() {
        while (true) {
          if (Boolean.parseBoolean(System.getProperty("exit", ""))) {
            try {
              serverSocket.close();
              return;
            } catch (IOException e) {
              System.out.println(e.toString());
            }
          }
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ignore) {}
        }
      }
    }).start();
    new Server(serverSocket, processors).listen();
  }

  private void listen() throws IOException {
    boolean cont = true;
    while (cont && !Boolean.parseBoolean(System.getProperty("exit", ""))) {
      try {
        Socket accept = myServerSocket.accept();
        log.warning("socket accepted");
        new ProcessingThread(accept).start();
      } catch (IOException ex) {
        System.out.println(ex.toString());
        cont = false;
      }
    }
  }

  class ProcessingThread extends Thread {
    Socket mySocket;
    public ProcessingThread(Socket socket) {
      mySocket = socket;
    }

    @Override
    public void run() {
      while (true) {
        try {
          log.warning("begin processing");
          ServerRequest serverRequest = Util.getRequest(mySocket);
          log.warning("end processing");
          if (serverRequest == null) {
            break;
          }
          ServerResponse response = getResponse(serverRequest);
          assert response != null;
          Util.sendResponse(mySocket, response);
        } catch (IOException e) {
          log.warning("something go wrong.." + e.toString());
          break;
        }
      }
    }

    private ServerResponse getResponse(ServerRequest serverRequest) {
      for (RequestProcessor processor : myProcessors) {
        ServerResponse serverResponse = processor.processRequest(serverRequest);
        if (serverResponse != null) {
          return serverResponse;
        }
      }
      return null;
    }
  }
}
