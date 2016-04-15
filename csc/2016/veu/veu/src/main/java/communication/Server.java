package communication;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static communication.Protocol.*;

public class Server {
  private final static Logger logger = LoggerFactory.getLogger(Server.class);

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
        public ServerResponse.Builder processRequest(ServerRequest request) {
          logger.warn("warning: empty request");
          return ServerResponse.newBuilder();
        }
      }
    };
    new Thread(new Runnable() {
      public void run() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
          for(String line = ""; !"exit".equals(line); ) {
            line = reader.readLine();
          }
        } catch (IOException ignore) {}

        if (!serverSocket.isClosed()) {
          try {
            serverSocket.close();
          } catch (IOException ignore) {}
        }
      }
    }).start();
    new Server(serverSocket, processors).listen();
  }

  private void listen() throws IOException {
    boolean cont = true;
    logger.info("server started, localhost:{}", myServerSocket.getLocalPort());
    while (cont && !Boolean.parseBoolean(System.getProperty("exit", ""))) {
      try {
        Socket accept = myServerSocket.accept();
        new ProcessingThread(accept).start();
      } catch (IOException ex) {
        logger.warn("Exception during listening", ex);
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
          ServerRequest serverRequest = Util.getRequest(mySocket);
          if (serverRequest == null) {
            break;
          }
          ServerResponse response = getResponse(serverRequest);
          assert response != null;
          Util.sendResponse(mySocket, response);
        } catch (IOException e) {
          logger.error("something go wrong..");
          try {
            mySocket.close();
          } catch (IOException e1) {
            logger.error("Error during closing socket", e1);
          }
          break;
        }
      }
    }

    private ServerResponse getResponse(ServerRequest request) {
      for (RequestProcessor processor : myProcessors) {
        ServerResponse.Builder builder = processor.processRequest(request);
        if (builder != null) {
          return builder.setRequestId(request.getRequestId()).build();
        }
      }
      return null;
    }
  }
}
