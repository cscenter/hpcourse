package client;


import communication.Protocol;

import java.util.List;

public class TaskClient implements Client {
    @Override
    public int submitTask(Protocol.Task task) {
        return 0;
    }

    @Override
    public int subscribe(int taskId) {
        return 0;
    }

    @Override
    public List<Protocol.ListTasksResponse.TaskDescription> getListTasksResponse() {
        return null;
    }
}
