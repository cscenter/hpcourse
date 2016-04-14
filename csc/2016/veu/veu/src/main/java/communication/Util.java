package communication;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.Socket;

public class Util {
  public static Protocol.ServerRequest getRequest(Socket socket) throws IOException {
    int size = socket.getInputStream().read();
    byte[] bytes = new byte[size];
    int read = socket.getInputStream().read(bytes);
    if (read == 0) {
      throw new IOException("empty request");
    }
    return Protocol.ServerRequest.parseFrom(bytes);
  }

  public static void main(String[] args) {
    Runnable runnable = new Runnable() {
      public void run() {
        System.out.println("before sleep!");
        try {
          Thread.sleep(2000);
        } catch (InterruptedException ignore) {}
        System.out.println("sasok!");
      }
    };
    Thread thread = new Thread(runnable);
    System.out.println("before running...");
    thread.start();
    System.out.println("after running!");

  }
}
