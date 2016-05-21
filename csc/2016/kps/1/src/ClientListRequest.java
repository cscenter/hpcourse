package first;


import java.net.Socket;

import static first.Protocol.ServerRequest;
import static first.Protocol.ListTasks;


public class ClientListRequest extends ClientRequest {

    public ClientListRequest(Socket socket,
                         String clientId,
                         long requestId) {
        this.socket = socket;
        this.clientId = clientId;
        this.requestId = requestId;
    }

    protected void setOther(ServerRequest.Builder sb) {
        sb.setList(ListTasks.newBuilder().build());
    }
}
    