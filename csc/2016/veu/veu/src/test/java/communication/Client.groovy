package communication

class Client extends Thread {
  public static int port = 9708

  String CLIENT_ID = 'bob'
  Socket socket
  OutputStream outp
  InputStream inp

  Client() {
    socket = new Socket('localhost', port)
    outp = socket.getOutputStream()
    inp = socket.getInputStream()
  }

  public static void main(String[] args) {
    new Client().start()
  }

  void run() {
    def firstTask = TestUtil.task('a:5, b:7, m:1, p:13, n:10000', CLIENT_ID, 1)
    outp.write(firstTask.serializedSize)
    firstTask.writeTo(outp)

    def sz = inp.read()
    byte[] bytes = new byte[sz]
    inp.read(bytes)
    Protocol.ServerResponse response = Protocol.ServerResponse.parseFrom(bytes)
    println response
  }

}
