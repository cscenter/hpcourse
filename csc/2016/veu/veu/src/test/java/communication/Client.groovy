package communication

import com.google.protobuf.GeneratedMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static communication.TestUtil.list
import static communication.TestUtil.subscribe
import static communication.TestUtil.task

class Client extends Thread {
  private final static Logger logger = LoggerFactory.getLogger(Client.class);

  private String clientId
  private Socket socket
  private OutputStream outp
  private InputStream inp
  private int cnt

  Client(String id) {
    socket = new Socket('localhost', ServerRunner.port)
    outp = socket.getOutputStream()
    inp = socket.getInputStream()
    clientId = id
    cnt = 0
    setName(getName() + ':' + clientId)
  }

  public static void main(String[] args)  {
    new Client('bob').start()
    new Client('alice').start()
    new Client('john').start()
    new Client('ivan').start()
    new Client('borya').start()
    new Client('svyatopolk').start()

    new Client2('znajka').start()
    new Client2('vintik').start()
    new Client2('kozlik').start()
  }

  static log(String msg) {
    synchronized (logger) {
      logger.debug(msg)
    }
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

    response = sendAndGet(task('m:239, n:10000000')).getSubmitResponse()
    assert response.getStatus() == Protocol.Status.OK
    def id2 = response.getSubmittedTaskId()
    log 'submit: ' + id2

    response = sendAndGet(task("a:_$id2, m:1000017, n:10000000")).getSubmitResponse()
    assert response.getStatus() == Protocol.Status.OK
    def id3 = response.getSubmittedTaskId()
    log 'submit: ' + id3

//    response = sendAndGet(list()).getListResponse()
//    log TestUtil.formatted(response)
//    sleep(3000)
//    response = sendAndGet(list()).getListResponse()
//    log TestUtil.formatted(response)
  }

  static class Client2 extends Client {
    Client2(String id) {
      super(id)
    }

    void run() {
      for (int i = 0; i < 5; i++) {
        def response = sendAndGet(list()).getListResponse()
        log TestUtil.formatted(response)
        sleep(5000)
      }
    }
  }
}
