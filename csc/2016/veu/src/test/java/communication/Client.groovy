package communication

import com.google.protobuf.GeneratedMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static communication.TestUtil.list
import static communication.TestUtil.subscribe
import static communication.TestUtil.task

class Client extends Thread {
  private final static Logger logger = LoggerFactory.getLogger(Client.class);

  protected String clientId
  protected Socket socket
  protected OutputStream outp
  protected InputStream inp
  protected int cnt

  Client(String id) {
    socket = new Socket('localhost', ServerRunner.port)
    outp = socket.getOutputStream()
    inp = socket.getInputStream()
    clientId = id
    cnt = 0
    setName(getName() + ':' + clientId)
  }

  public static void main(String[] args)  {
//    new Client('bob').start()
//    new Client('alice').start()
//    new Client('john').start()
//    new Client('ivan').start()
//    new Client('borya').start()
//    new Client('svyatopolk').start()

    new IllegalClient('rediska').start();

//    new ListClient('znajka').start()
//    new ListClient('vintik').start()
    new ListClient('kozlik').start()
  }

  static log(String msg) {
    logger.debug(msg)
  }
  protected Protocol.ServerResponse sendAndGet(GeneratedMessage msg) {
    TestUtil.sendRequest(socket, clientId, cnt++, msg)
  }

  void run() {
    log socket.toString()
    def response = sendAndGet(task('a:5, b:7, m:239, p:13, n:100000')).getSubmitResponse()
    assert response.getStatus() == Protocol.Status.OK
    def id = response.getSubmittedTaskId()
    log 'submit: ' + id

    response = sendAndGet(subscribe(id)).getSubscribeResponse()
    log "subscribe $id: $response"

    response = sendAndGet(task('m:239, n:100000000')).getSubmitResponse()
    assert response.getStatus() == Protocol.Status.OK
    def id2 = response.getSubmittedTaskId()
    log 'submit: ' + id2

    response = sendAndGet(subscribe(id2)).getSubscribeResponse()
    log "subscribe $id2: $response"

    response = sendAndGet(task("a:_$id2, m:1000017, n:1000000000")).getSubmitResponse()
    assert response.getStatus() == Protocol.Status.OK
    def id3 = response.getSubmittedTaskId()
    log 'submit: ' + id3

    response = sendAndGet(subscribe(id3)).getSubscribeResponse()
    log "subscribe $id3: $response"

//    response = sendAndGet(list()).getListResponse()
//    log TestUtil.formatted(response)
//    sleep(3000)
//    response = sendAndGet(list()).getListResponse()
//    log TestUtil.formatted(response)
  }

  static class ListClient extends Client {
    ListClient(String id) {
      super(id)
    }

    void run() {
      while (true) {
        def response = sendAndGet(list()).getListResponse()
        def formatted = TestUtil.formatted(response)
        log formatted
        if (formatted.endsWith(TestUtil.DONE)) {
          break
        }
        sleep(5000)
    }
    }
  }

  static class IllegalClient extends Client {
    IllegalClient(String id) {
      super(id)
    }

    void run() {
      log socket.toString()
      def response = sendAndGet(task('a:5, b:7, m:0, p:13, n:100000')).getSubmitResponse()
      assert response.getStatus() == Protocol.Status.OK
      def id = response.getSubmittedTaskId()
      log 'submit: ' + id

      response = sendAndGet(subscribe(id)).getSubscribeResponse()
      log "subscribe $id: $response"

      response = sendAndGet(task("m:_$id, n:100000000")).getSubmitResponse()
      assert response.getStatus() == Protocol.Status.OK
      def id2 = response.getSubmittedTaskId()
      log 'submit: ' + id2

      response = sendAndGet(subscribe(id2)).getSubscribeResponse()
      log "subscribe $id2: $response"
    }
  }
}
