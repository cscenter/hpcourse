package server.processors;

import communication.Protocol;

import java.net.Socket;

/**
 * @author nikita.sokeran@emc.com
 */
public class ListTasksProcessor extends BaseTaskProcessor {
    protected ListTasksProcessor(final Socket socket, final Protocol.ServerRequest request) {
        super(socket, request);
    }

    @Override
    public void run() {

    }
}
