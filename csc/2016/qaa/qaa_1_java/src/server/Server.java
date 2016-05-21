package server;

import communication.Protocol;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public interface Server {
    Protocol.SubmitTaskResponse submitTask(Protocol.SubmitTask request, String clientId);
    Protocol.SubscribeResponse subscribe(Protocol.Subscribe request);
    Protocol.ListTasksResponse listTasks(Protocol.ListTasks request);
}
