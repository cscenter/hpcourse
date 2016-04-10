package client;

import client.params.MessageParams;
import client.params.SubmitParams;
import client.params.SubscribeParams;
import com.google.protobuf.GeneratedMessage;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
public enum MessageManager {
    SUBMIT(0) {
        @Override
        public GeneratedMessage generate(MessageParams<?> params) {
            if (!(params instanceof SubmitParams))
                throw new IllegalArgumentException("Must be SubmitParams");

            SubmitTask.Builder builder = SubmitTask.newBuilder();
            ((SubmitParams) params).configure(builder);
            return builder.build();
        }

        @Override
        public void setTask(ServerRequest.Builder builder, GeneratedMessage message) {
            builder.setSubmit((SubmitTask)message);
        }

        @Override
        String description(ServerResponse response) {
            SubmitTaskResponse submitTaskResponse = response.getSubmitResponse();
            StringBuilder sb = new StringBuilder();
            sb.append("\tSubmit task response\n");
            sb.append(String.format("\tStatus: %s\n", submitTaskResponse.getStatus()));
            sb.append(String.format("\tTask id: %s\n", submitTaskResponse.getSubmittedTaskId()));
            return sb.toString();
        }
    },
    SUBSCRIBE(1) {
        @Override
        public GeneratedMessage generate(MessageParams<?> params) {
            if (!(params instanceof SubscribeParams))
                throw new IllegalArgumentException("Must be SubscribeParams");

            Subscribe.Builder builder = Subscribe.newBuilder();
            ((SubscribeParams) params).configure(builder);
            return builder.build();
        }

        @Override
        public void setTask(ServerRequest.Builder builder, GeneratedMessage message) {
            builder.setSubscribe((Subscribe)message);
        }

        @Override
        String description(ServerResponse response) {
            SubscribeResponse subscribeResponse = response.getSubscribeResponse();
            StringBuilder sb = new StringBuilder();
            sb.append("\tSubscribe response\n");
            sb.append(String.format("\tStatus: %s\n", subscribeResponse.getStatus()));
            sb.append(String.format("\tValue: %s\n", subscribeResponse.getValue()));
            return sb.toString();
        }
    },
    LIST(2) {
        @Override
        public GeneratedMessage generate(MessageParams<?> params) {
            return ListTasks.newBuilder().build();
        }

        @Override
        public void setTask(ServerRequest.Builder builder, GeneratedMessage message) {
            builder.setList((ListTasks)message);
        }

        @Override
        String description(ServerResponse response) {
            ListTasksResponse listTasksResponse = response.getListResponse();
            StringBuilder sb = new StringBuilder();
            sb.append("\tList task response\n");
            sb.append(String.format("\tStatus: %s\n", listTasksResponse.getStatus()));
            for (ListTasksResponse.TaskDescription taskDescription : listTasksResponse.getTasksList()) {
                sb.append(String.format("\tTask id: %s\n", taskDescription.getTaskId()));
                sb.append(String.format("\tClient id: %s\n", taskDescription.getClientId()));
                if (taskDescription.hasResult()) {
                    sb.append(String.format("\tValue: %s\n", taskDescription.getResult()));
                } else {
                    sb.append("\tNot calculated\n");
                }
            }
            return sb.toString();
        }
    };

    private final int number;

    MessageManager(int number) {
        this.number = number;
    }

    public String getText(ServerResponse response, String clientId) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Request id: %s\n", response.getRequestId()));
        sb.append(String.format("Client id: %s\n", clientId));
        sb.append(description(response));
        return sb.toString();
    }

    public abstract GeneratedMessage generate(MessageParams<?> params);
    public abstract void setTask(ServerRequest.Builder builder, GeneratedMessage message);
    abstract String description(ServerResponse response);

    public static MessageManager of(int number) {
        for (MessageManager messageManager : values()) {
            if (number == messageManager.number)
                return messageManager;
        }
        throw new IllegalArgumentException("No message wrapper for: " + number);
    }
}
