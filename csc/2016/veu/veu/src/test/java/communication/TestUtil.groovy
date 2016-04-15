package communication

import communication.Protocol.Task.Param

class TestUtil {
  private static Random RAND = new Random()
  private static int MAX = 200000

  static def list = Protocol.ListTasks.newBuilder().build()

  static def list() {
    list
  }

  static def task(String val) {
    def params = [:].withDefault { RAND.nextInt(MAX) as String }
    val.split(', ').each({ def x = it.split(':'); params.put(x[0], x[1]) })
    Protocol.Task.newBuilder()
      .setA(toParam(params['a']))
      .setB(toParam(params['b']))
      .setP(toParam(params['p']))
      .setM(toParam(params['m']))
      .setN(params['n'] as Integer).build();
  }

  static def taskToString(Protocol.Task task) {
    StringBuilder builder = new StringBuilder()
    builder.append('task: ')
    def params = [:]
    def EMPTY = [] as Object[]
    'abpm'.toCharArray().each {
      Param p = task.invokeMethod("get${it.toUpperCase()}",  EMPTY)
      params[it] = fromParam(p)
    }
    params
  }

  static def subscribe(int id) {
    Protocol.Subscribe.newBuilder().setTaskId(id).build()
  }

  static def sendRequest(Socket socket, String clientId, int requestId, def msg) {
    def request = request(clientId, requestId, msg)
    socket.getOutputStream().write(request.serializedSize)
    request.writeTo(socket.getOutputStream())

    def sz = socket.getInputStream().read()
    byte[] bytes = new byte[sz]
    socket.getInputStream().read(bytes)
    return Protocol.ServerResponse.parseFrom(bytes)
  }

  static def request(String client, int requestId, def object) {
    def builder = Protocol.ServerRequest.newBuilder().setClientId(client).setRequestId(requestId)
    if (object instanceof Protocol.Task) {
      builder.setSubmit(Protocol.SubmitTask.newBuilder().setTask(object).build())
    } else if (object instanceof Protocol.Subscribe) {
      builder.setSubscribe(object)
    } else if (object instanceof Protocol.ListTasks) {
      builder.setList(object)
    } else {
      assert false;
    }
    builder.build()
  }

  // format: task 'a:_44, b:_0, m:1, p:13, n:55'
  static def toParam(def str) {
    def builder = Protocol.Task.Param.newBuilder()
    if (str.startsWith('_')) {
      builder.setDependentTaskId(str.substring(1) as Integer)
    } else {
      builder.setValue(str as Integer)
    }
    builder.build()
  }

  static def fromParam(Param param) {
    param.hasDependentTaskId() ? '_' + param.getDependentTaskId() :
      (param.hasValue() ? param.getValue() : '???')
  }

  static def formatted(Protocol.ListTasksResponse listTasksResponse) {
    StringBuilder builder = new StringBuilder()
    def status = listTasksResponse.getStatus()
    builder.append("LIST: $status\n")
    listTasksResponse.getTasksList().each( {
      def result = it.hasResult() ? it.getResult() as String : 'empty'
      def format = String.format(
        "id: %3s, task: %50s, client: %5s, result: %s\n", it.taskId, taskToString(it.task), it.clientId, result)
      builder.append(format);
    })
    builder.toString()
  }
}
