package first;


import java.net.Socket;

import static first.Protocol.ServerRequest;
import static first.Protocol.SubmitTask;
import static first.Protocol.Task;


public class ClientSubmitRequest extends ClientRequest {
    
    public ClientSubmitRequest(Socket socket,
                         String clientId,
                         long requestId) {
        this.socket = socket;
        this.clientId = clientId;
        this.requestId = requestId;
    }
    
    protected void setOther(ServerRequest.Builder sb) {
        Task task = Task.newBuilder()
                        .setA(getParam(3))
                        .setB(getParam(4))
                        .setP(getParam(5))
                        .setM(getParam(6))
                        .setN(7)
                        .build();
        sb.setSubmit(SubmitTask.newBuilder()
                        .setTask(task)
                        .build());
    }
    
    private Task.Param getParam(int x) {    
        return Task.Param.newBuilder()
                    .setValue(x)
                    .build();
    }
}
