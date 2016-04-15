package server.processors;

import communication.Protocol;
import javafx.util.Pair;
import util.ConcurrentStorage;
import util.TaskAndResult;

import java.net.Socket;

/**
 * @author nikita.sokeran@emc.com
 */
public class ListTasksProcessor extends BaseTaskProcessor {


    protected ListTasksProcessor(final ConcurrentStorage<TaskAndResult> concurrentStorage, final Socket socket, final Protocol.ServerRequest request) {
        super(concurrentStorage, socket, request);
    }

    @Override
    public void run() {

    }
}
