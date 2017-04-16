package client;

import communication.Protocol;

/**
 * Created by qurbonzoda on 15.04.16.
 */
public interface Client {
    void submitTaskResponse(Protocol.ServerResponse response);
    void subscribeResponse(Protocol.ServerResponse response);
    void listTasksResponse(Protocol.ServerResponse response);
}
