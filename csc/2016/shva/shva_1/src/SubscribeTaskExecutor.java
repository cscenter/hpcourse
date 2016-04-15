import communication.Protocol;

public class SubscribeTaskExecutor {

    private int taskId;

    public SubscribeTaskExecutor(int taskId) {
        this.taskId = taskId;
    }

    public long startSubscribeTask() {
        if (RequestsHistory.getTaskDescriptionById(taskId).isDone()) {
            return RequestsHistory.getTaskDescriptionById(taskId).getResult();
        } else {
            Protocol.Task task = RequestsHistory.getTaskDescriptionById(taskId).getTask();
            synchronized (task) {
                while (!RequestsHistory.getTaskDescriptionById(taskId).isDone()) {
                    try {
                        task.wait();
                    } catch (InterruptedException e) {}
                }
                return RequestsHistory.getTaskDescriptionById(taskId).getResult();
            }
        }
    }

}
