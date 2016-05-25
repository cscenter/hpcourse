package server.processingThreads;

import server.Controller;
import utils.Task;
import utils.TaskParameter;
import communication.Protocol;

import java.io.IOException;
import java.net.Socket;
/*  Обработчик запросов на запуск новой задачи*/

public class SubmitTaskProcessingThread extends RequestProcessingThread {

    public SubmitTaskProcessingThread(Socket socket, Protocol.ServerRequest serverRequest) {
        super(socket, serverRequest);
    }

    @Override
    public void run() {
        Protocol.Task currentRequest = serverRequest.getSubmit().getTask();
        Protocol.SubmitTaskResponse.Builder builder = Protocol.SubmitTaskResponse.newBuilder();

        // запуск задачи на выполнение
        try {
            TaskParameter a = getTaskParameter(currentRequest.getA());
            TaskParameter b = getTaskParameter(currentRequest.getB());
            TaskParameter p = getTaskParameter(currentRequest.getP());
            TaskParameter m = getTaskParameter(currentRequest.getM());
            TaskParameter n = new TaskParameter(currentRequest.getN());
            int currentTaskId = Controller.getInstance().addTask(a, b, p, m, n, serverRequest.getClientId());
            builder.setSubmittedTaskId(currentTaskId);
            builder.setStatus(Protocol.Status.OK);
        } catch (InvalidTaskParameterException e) {
            builder.setStatus(Protocol.Status.ERROR);
        }

        // отправка сообщения клиенту
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(serverRequest.getRequestId());
        serverResponseBuilder.setSubmitResponse(builder.build());

        try {
            send(serverResponseBuilder.build());
        } catch (IOException e) {
            System.out.println("Error sending data to " + socket.getInetAddress());
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {}
        }
    }

    /*  получение параметров задачи из запроса клиента */
    private TaskParameter getTaskParameter(Protocol.Task.Param parameter) throws InvalidTaskParameterException {
        if (parameter.hasValue()) {
            return new TaskParameter(parameter.getValue());
        } else {
            Task originTask = Controller.getInstance().getTaskById(parameter.getDependentTaskId());
            if (originTask == null) {
                throw new InvalidTaskParameterException();
            }
            return new TaskParameter(originTask);
        }
    }
}
