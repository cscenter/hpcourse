package ru.csc.roman_fedorov;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import communication.Protocol;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;


/**
 * Created by roman on 15.04.2016.
 */

public class SocketConnectionThread extends Thread {
    private Socket socket = null;

    public SocketConnectionThread(Socket socket) {
        super("SocketConnectionThread");
        this.socket = socket;
    }

    private long task(long a, long b, long p, long m, long n) {
        while (n-- > 0) {
            b = (a * p + b) % m;
            a = b;
        }
        return a;
    }


    private long[] getTaskParams(Protocol.Task taskToParse) {
        long a, b, p, m, n;
        try {
            if (taskToParse.getA().hasValue()) {
                a = taskToParse.getA().getValue();
            } else { // has dependent task
                synchronized (Server.m.get(taskToParse.getA().getDependentTaskId())) {
                    CustomTaskDescription taskDescription = Server.m.get(taskToParse.getA().getDependentTaskId());
                    if (taskDescription.getResult() < 0L) { // task is not already done, wait until it finish
                        taskDescription.wait();
                    }
                    a = taskDescription.getResult();
                }
            }
            if (taskToParse.getB().hasValue()) {
                b = taskToParse.getB().getValue();
            } else { // has dependent task
                synchronized (Server.m.get(taskToParse.getB().getDependentTaskId())) {
                    CustomTaskDescription taskDescription = Server.m.get(taskToParse.getB().getDependentTaskId());
                    if (taskDescription.getResult() < 0L) { // task is not already done, wait until it finish
                        taskDescription.wait();
                    }
                    b = taskDescription.getResult();
                }
            }
            if (taskToParse.getP().hasValue()) {
                p = taskToParse.getP().getValue();
            } else { // has dependent task
                synchronized (Server.m.get(taskToParse.getP().getDependentTaskId())) {
                    CustomTaskDescription taskDescription = Server.m.get(taskToParse.getP().getDependentTaskId());
                    if (taskDescription.getResult() < 0L) { // task is not already done, wait until it finish
                        taskDescription.wait();
                    }
                    p = taskDescription.getResult();
                }
            }
            if (taskToParse.getM().hasValue()) {
                m = taskToParse.getM().getValue();
            } else { // has dependent task
                synchronized (Server.m.get(taskToParse.getM().getDependentTaskId())) {
                    CustomTaskDescription taskDescription = Server.m.get(taskToParse.getM().getDependentTaskId());
                    if (taskDescription.getResult() < 0L) { // task is not already done, wait until it finish
                        taskDescription.wait();
                    }
                    m = taskDescription.getResult();
                }
            }
            if (m == 0L) {
                throw new RuntimeException("Modulo number can't be zero");
            }
            n = taskToParse.getN();
        } catch (Exception e) {
            return null;
        }
        return new long[]{a, b, p, m, n};
    }

    @Override
    public void run() {
        try {
            CodedInputStream inputStream = CodedInputStream.newInstance(socket.getInputStream());
            int messageLength = inputStream.readRawVarint32();
            Protocol.ServerRequest request = Protocol.ServerRequest.parseFrom(inputStream.readRawBytes(messageLength));

            if (request.hasSubmit()) {
                int id;
                long[] params = getTaskParams(request.getSubmit().getTask());
                if (params == null) {    // Error during extracting params from task
                    sendResponse(getTaskResponse(-1, false));
                    return;
                } else {
                    id = Server.idCounter.getAndIncrement();
                    Server.m.put(id, new CustomTaskDescription(params, request.getClientId(), -1L));
                    sendResponse(getTaskResponse(id, true));
                }

                long result = task(params[0], params[1], params[2], params[3], params[4]);
                synchronized (Server.m.get(id)) {
                    CustomTaskDescription description = Server.m.get(id);
                    description.setResult(result);
                    description.notifyAll();
                }
            } else if (request.hasSubscribe()) {
                int requested_id = request.getSubscribe().getTaskId();
                try {
                    CustomTaskDescription description;
                    long value;
                    synchronized (Server.m.get(requested_id)) {
                        description = Server.m.get(requested_id);
                        if (description.getResult() < 0L) {     // task is not done
                            description.wait();
                        }
                        value = description.getResult();
                    }
                    sendResponse(getSubscribeResponse(value, true));
                } catch (Exception e) {
                    sendResponse(getSubscribeResponse(-1L, false));
                }
            } else if (request.hasList()) {
                Set<Integer> keySet = Server.m.keySet();
                CustomTaskDescription[] tasks;
                synchronized (Server.m) {
                    tasks = new CustomTaskDescription[keySet.size()];
                    for (Integer id : keySet) {
                        tasks[id] = Server.m.get(id);
                    }
                }
                sendResponse(getListTasksResponse(tasks));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Protocol.SubscribeResponse getSubscribeResponse(Long value, boolean isSuccessfullySubscribed) {
        return Protocol.SubscribeResponse.newBuilder()
                .setStatus(isSuccessfullySubscribed ? Protocol.Status.OK : Protocol.Status.ERROR)
                .setValue(value)
                .build();
    }

    private Protocol.SubmitTaskResponse getTaskResponse(int id, boolean isSuccessfullyStarted) {
        return Protocol.SubmitTaskResponse.newBuilder()
                .setStatus(isSuccessfullyStarted ? Protocol.Status.OK : Protocol.Status.ERROR)
                .setSubmittedTaskId(id)
                .build();
    }

    private Protocol.ListTasksResponse getListTasksResponse(CustomTaskDescription[] tasks) {
        Protocol.ListTasksResponse.Builder builder =
                Protocol.ListTasksResponse.newBuilder().setStatus(Protocol.Status.OK);
        for (int i = 0; i < tasks.length; i++) {
            Protocol.ListTasksResponse.TaskDescription.Builder taskDescriptionBuilder =
                    Protocol.ListTasksResponse.TaskDescription.newBuilder();
            Protocol.Task.Builder taskBuilder = Protocol.Task.newBuilder();
            Protocol.Task.Param.Builder paramBuilder = Protocol.Task.Param.newBuilder();
            CustomTaskDescription currentTask = tasks[i];

            taskBuilder.setA(currentTask.getA().hasValue ? paramBuilder.setValue(currentTask.getA().value).build() :
                    paramBuilder.setDependentTaskId(currentTask.getA().dependentTaskId).build())
                    .setB(currentTask.getB().hasValue ? paramBuilder.setValue(currentTask.getB().value).build() :
                            paramBuilder.setDependentTaskId(currentTask.getB().dependentTaskId).build())
                    .setP(currentTask.getP().hasValue ? paramBuilder.setValue(currentTask.getP().value).build() :
                            paramBuilder.setDependentTaskId(currentTask.getP().dependentTaskId).build())
                    .setM(currentTask.getM().hasValue ? paramBuilder.setValue(currentTask.getM().value).build() :
                            paramBuilder.setDependentTaskId(currentTask.getM().dependentTaskId).build())
                    .setN(currentTask.getN());

            taskDescriptionBuilder
                    .setTaskId(i)
                    .setClientId(tasks[i].getClientId())
                    .setTask(taskBuilder.build());
            if (currentTask.getResult() > -1L) {
                taskDescriptionBuilder.setResult(currentTask.getResult());
            }
            builder.addTasks(taskDescriptionBuilder.build());
        }
        return builder.build();
    }

    private void sendResponse(com.google.protobuf.GeneratedMessage response) {
        try {
            CodedOutputStream outputStream = CodedOutputStream.newInstance(socket.getOutputStream());
            outputStream.writeRawVarint32(response.getSerializedSize());
            outputStream.flush();
            response.writeTo(outputStream);
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
