import com.google.protobuf.GeneratedMessage;
import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;


//Для удобства формирования ответов на запросы клиентов
public abstract class BaseTask extends Thread{

    private long requestId;
    private Socket socket;

    BaseTask(Socket socket, long requestId) {
        this.requestId = requestId;
        this.socket = socket;
    }

    void sendResponse(GeneratedMessage message) {
        Protocol.ServerResponse.Builder response = Protocol.ServerResponse.newBuilder();
        response.setRequestId(requestId);

        if(message instanceof Protocol.ListTasksResponse) {
            response.setListResponse((Protocol.ListTasksResponse) message);
        }

        if(message instanceof Protocol.SubmitTaskResponse) {
            response.setSubmitResponse((Protocol.SubmitTaskResponse) message);
        }

        if(message instanceof Protocol.SubscribeResponse) {
            response.setSubscribeResponse((Protocol.SubscribeResponse) message);
        }

        Protocol.ServerResponse answer = response.build();

        try {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(answer.getSerializedSize());
            answer.writeTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
