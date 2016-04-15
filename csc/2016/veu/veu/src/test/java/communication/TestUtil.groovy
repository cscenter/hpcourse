package communication

class TestUtil {
  static def task(String val, String client, int requestId) {
    def params = [:]
    val.split(', ').each({ def x = it.split(':'); params.put(x[0], x[1]) })
    Protocol.Task task = Protocol.Task.newBuilder()
      .setA(toParam(params['a']))
      .setB(toParam(params['b']))
      .setP(toParam(params['p']))
      .setM(toParam(params['m']))
      .setN(params['n'] as Integer).build();

    Protocol.ServerRequest.newBuilder()
      .setClientId(client)
      .setRequestId(requestId)
      .setSubmit(Protocol.SubmitTask.newBuilder().setTask(task).build()).build()
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
}
