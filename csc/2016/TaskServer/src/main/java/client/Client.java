import communication.Protocol;

import java.util.List;

public class Client {

    Client() {

    }

    public int submitTask(Protocol.Task task) {

        return 1;
    }


    public int subscribe(int taskId) {
        return 1;
    }

    public List<Protocol.ListTasksResponse.TaskDescription> getListTasksResponse() {
        return null;
    }


}
