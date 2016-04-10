package client.params;

import communication.Protocol;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
public class SubscribeParams implements MessageParams<Subscribe.Builder> {
    private final int taskId;

    public SubscribeParams(int taskId) {
        this.taskId = taskId;
    }

    @Override
    public void configure(Subscribe.Builder message) {
        message.setTaskId(taskId);
    }
}
