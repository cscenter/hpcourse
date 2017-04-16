package server.processors;

import communication.Protocol;
import javafx.util.Pair;
import util.ConcurrentStorage;
import util.TaskAndResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public abstract class BaseTaskProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(BaseTaskProcessor.class.getName());

    protected ConcurrentStorage<TaskAndResult> concurrentStorage;
    protected Socket socket;
    protected Protocol.ServerRequest request;

    protected BaseTaskProcessor(final ConcurrentStorage<TaskAndResult> concurrentStorage, final Socket socket, final Protocol.ServerRequest request) {
        this.concurrentStorage = concurrentStorage;
        this.socket = socket;
        this.request = request;
    }
}
