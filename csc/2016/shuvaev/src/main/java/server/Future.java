package server;

import communication.Protocol;

public class Future {
    private Protocol.WrapperMessage message;

    public boolean isDone() {
        return message != null;
    }

    public Protocol.WrapperMessage getMessage() {
        return message;
    }

    public void setMessage(Protocol.WrapperMessage message) {
        this.message = message;
    }
}
