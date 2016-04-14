import communication.Protocol;

import java.util.List;

public interface Client {


    public int submitTask(Protocol.Task task);

    public int subscribe(int taskId);

    public List<Protocol.ListTasksResponse.TaskDescription> getListTasksResponse();


}
