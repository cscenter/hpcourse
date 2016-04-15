package communication

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static communication.TestUtil.list
import static communication.TestUtil.subscribe
import static communication.TestUtil.task

class Client extends Thread {
  private final static Logger logger = LoggerFactory.getLogger(Client.class);

  String clientId
  Socket socket
  OutputStream outp
  InputStream inp

  Client(String id) {
    socket = new Socket('localhost', ServerRunner.port)
    outp = socket.getOutputStream()
    inp = socket.getInputStream()
    clientId = id
  }

  public static void main(String[] args)  {
    new Client('bob').start()
    new Client('alice').start()
  }

  static log(String msg) {
    synchronized (logger) {
      logger.debug(msg)
    }
  }

  void run() {
    log socket.toString()
    def cnt = 0
    def sendAndGet = { TestUtil.sendRequest(socket, clientId, cnt++, it) }
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

    response = sendAndGet(list()).getListResponse()
    log TestUtil.formatted(response)

    sleep(3000)

    response = sendAndGet(list()).getListResponse()
    log TestUtil.formatted(response)
  }

}
