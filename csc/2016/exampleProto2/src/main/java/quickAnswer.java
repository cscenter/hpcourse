import communication.Protocol;

import java.io.IOException;
import java.net.Socket;

/**
 * Created by Alex on 17.04.2016.
 */
/*добавлено, так как не имел функциональности отвечать сразу, в случае, если нет dependentTask
чтобы не городить ifами вынес эту логику сюда
 */
public class quickAnswer {
    public static void quickAnswer(Protocol.Task task, int numOfTask, Socket socket) {
        boolean isThereDependentTaskOrZero = task.getA().hasDependentTaskId() || task.getB().hasDependentTaskId() ||
                task.getM().hasDependentTaskId() || task.getP().hasDependentTaskId();
        //тогда не отвечаем быстро
        if (isThereDependentTaskOrZero){
            return;
        } else {
            Protocol.WrapperMessage.Builder wrapMessage= Protocol.WrapperMessage.newBuilder();
            Protocol.ServerResponse.Builder serverResponse= Protocol.ServerResponse.newBuilder();
            Protocol.SubmitTaskResponse.Builder submitTask= Protocol.SubmitTaskResponse.newBuilder();
            //принята на обработку
            if(task.getN()!=0) {
                submitTask.setStatus(Protocol.Status.OK);
            } else {
                submitTask.setStatus(Protocol.Status.ERROR);
            }
            submitTask.setSubmittedTaskId(numOfTask);
            serverResponse.setSubmitResponse(submitTask.build());
            //в моём понимание request_id и номер task имеют те же номера, так как не было сказано иного,
            //я не стал выдумывать и установил соотвествие
            serverResponse.setRequestId((long) numOfTask);
            wrapMessage.setResponse(serverResponse.build());
            try {
                //посылаем быстрый ответ
                wrapMessage.build().writeDelimitedTo(socket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("I could not send a response! ");
            }
        }
    }
}
