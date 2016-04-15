package first;


import java.net.Socket;

import static first.Protocol.ServerRequest;
import static first.Protocol.Subscribe;


public class ClientSubscribeRequest extends ClientRequest {

    private int taskId;
    
    public ClientSubscribeRequest(Socket socket,
                         String clientId,
                         long requestId,
                         int taskId) {
        this.socket = socket;
        this.clientId = clientId;
        this.requestId = requestId;
        this.taskId = taskId;
    }
    
    protected void setOther(ServerRequest.Builder sb) {
        Subscribe subscribe = Subscribe.newBuilder()
                                .setTaskId(taskId)
                                .build();
        sb.setSubscribe(subscribe);
    }
}