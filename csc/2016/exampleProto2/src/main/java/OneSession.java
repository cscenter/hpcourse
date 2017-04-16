import communication.Protocol;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Alex on 08.04.2016.
 */
public class OneSession {
    boolean hasSubmit;
    boolean hasSubscribe;
    boolean hasList;
    long val;
    int stage;//2-решена , 1-ещё не решена
    int request_id;
    boolean statusTask;
    long subscribeTaskId;
    boolean statusSubscribeTask;
    long valSubscribeTask;
    String client_id;
    Protocol.Task task;

    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

    public void setRequest_id(int request_id) {
        this.request_id = request_id;
    }

    public void setTask(Protocol.Task task) {
        this.task = task;
    }

    public void setVal(long val) {
        this.val = val;
    }

    public int getRequest_id() {
        return request_id;
    }

    public Protocol.Task getTask() {
        return task;
    }

    public OneSession() {
        statusTask = true;
        statusSubscribeTask = true;
        hasSubmit = false;
        hasSubscribe = false;
        hasList = false;
    }

    public OneSession(long val, int stage, String client_id, Protocol.Task task) {
        this();
        this.val = val;
        this.stage = stage;
        this.client_id = client_id;
        this.task = task;
    }

    public static void MySessionChange(long val, int stage, String client_id, Protocol.Task task, OneSession session) {
        session.val = val;
        session.stage = stage;
        session.client_id = client_id;
        session.task = task;
    }

    public Protocol.WrapperMessage createResponse(HashMap<Long, OneSession> map) {
        Protocol.WrapperMessage.Builder wrapperMessage = Protocol.WrapperMessage.newBuilder();
        Protocol.ServerResponse.Builder serverResponseBuilder = Protocol.ServerResponse.newBuilder();
        serverResponseBuilder.setRequestId(request_id);
        Protocol.Status status;
        if (statusTask) {
            status = Protocol.Status.OK;
        } else {
            status = Protocol.Status.ERROR;
        }
        if (hasSubmit) {
            Protocol.SubmitTaskResponse.Builder submitTaskBuilder = Protocol.SubmitTaskResponse.newBuilder();
            submitTaskBuilder.setSubmittedTaskId(request_id);
            submitTaskBuilder.setStatus(status);
            serverResponseBuilder.setSubmitResponse(submitTaskBuilder.build());
        }
        if (hasSubscribe) {
            Protocol.SubscribeResponse.Builder subscribeResponseBuilder = Protocol.SubscribeResponse.newBuilder();
            Protocol.Status statusSubscribe;
            if (statusSubscribeTask) {
                statusSubscribe = Protocol.Status.OK;
            } else {
                statusSubscribe = Protocol.Status.ERROR;
            }
            subscribeResponseBuilder.setStatus(statusSubscribe);
            if (statusSubscribeTask) {
                subscribeResponseBuilder.setValue(val);
            }
            serverResponseBuilder.setSubscribeResponse(subscribeResponseBuilder.build());
        }
        if (hasList) {
            Protocol.ListTasksResponse.Builder listTaskBuilder = Protocol.ListTasksResponse.newBuilder();
            listTaskBuilder.setStatus(status);
            ArrayList<Protocol.ListTasksResponse.TaskDescription> arrayList
                    = new ArrayList<Protocol.ListTasksResponse.TaskDescription>();
            synchronized (map) {
                for (Long key: map.keySet()) {
                    arrayList.add(createTaskDescription(map.get(key)));
                }
            }
            listTaskBuilder.addAllTasks(arrayList);
            serverResponseBuilder.setListResponse(listTaskBuilder.build());
        }
        return wrapperMessage.setResponse(serverResponseBuilder.build()).build();
    }
    public Protocol.ListTasksResponse.TaskDescription createTaskDescription(OneSession session){
        Protocol.ListTasksResponse.TaskDescription.Builder descriptionBuilder=
                Protocol.ListTasksResponse.TaskDescription.newBuilder();
        descriptionBuilder.setTask(session.task);
        descriptionBuilder.setClientId(session.client_id);
        if(session.statusTask) {
            descriptionBuilder.setResult(session.val);
        }
        descriptionBuilder.setTaskId(session.request_id);
        return descriptionBuilder.build();
    }
}
