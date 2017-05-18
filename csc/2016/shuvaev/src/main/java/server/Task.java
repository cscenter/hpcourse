package server;

import communication.Protocol;
import server.Future;

public class Task {
    private Protocol.WrapperMessage message;
    private Future future;
    private Object monitor;


    public Task(Protocol.WrapperMessage message, Future future, Object monitor) {
        this.message = message;
        this.future = future;
        this.monitor = monitor;
    }

    public Protocol.WrapperMessage getMessage() {
        return message;
    }

    public Future getFuture() {
        return future;
    }

    public Object getMonitor() {
        return monitor;
    }
}
