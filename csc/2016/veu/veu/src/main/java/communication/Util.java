package communication;

import communication.Protocol.ServerRequest;
import org.jetbrains.annotations.Nullable;
import org.slf4j.*;

import java.io.*;
import java.net.Socket;

public class Util {
  private static Logger logger = LoggerFactory.getLogger(Util.class);

  @Nullable
  public static ServerRequest getRequest(Socket socket) throws IOException {
    int size = socket.getInputStream().read();
    if (size <= 0) {
      return null;
    }
    byte[] bytes = new byte[size];
    int read = socket.getInputStream().read(bytes);
    if (read <= 0) {
      throw new IOException("empty request");
    }
    return ServerRequest.parseFrom(bytes);
  }

  public static void sendResponse(Socket socket, Protocol.ServerResponse response) throws IOException {
    int responseSize = response.getSerializedSize();
    String type = response.hasListResponse() ? "list" :
      (response.hasSubmitResponse() ? "submit" :
        (response.hasSubscribeResponse() ? "subscribe" : "???"));
    logger.debug("response: {}, size: {}", type, responseSize);
    socket.getOutputStream().write(responseSize);
    socket.getOutputStream().write(response.toByteArray());
//    response.writeTo();
  }

  public static long solve(long a, long b, long p, long m, long n) {
    while (n-- > 0) {
      b = (a * p + b) % m;
      a = b;
    }
    return a;
  }
}
