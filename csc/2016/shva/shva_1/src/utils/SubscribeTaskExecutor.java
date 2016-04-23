package utils;

import communication.Protocol;

public class SubscribeTaskExecutor {

    private int taskId;

    public SubscribeTaskExecutor(int taskId) {
        this.taskId = taskId;
    }

    public long startSubscribeTask() throws IllegalArgumentException {
        TaskDescription taskDescription = RequestsHistory.getTaskDescriptionById(taskId);
        if (taskDescription == null) {
            throw new IllegalArgumentException();
        }
        if (taskDescription.isDone()) {
            return RequestsHistory.getTaskDescriptionById(taskId).getResult();
        } else {
            Protocol.Task task = taskDescription.getTask();
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
