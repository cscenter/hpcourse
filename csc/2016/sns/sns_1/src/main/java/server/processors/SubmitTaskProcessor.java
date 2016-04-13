package server.processors;

import communication.Protocol;

import java.net.Socket;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class SubmitTaskProcessor extends BaseTaskProcessor {

    private final static Logger LOGGER = Logger.getLogger(SubmitTaskProcessor.class.getName());

    protected SubmitTaskProcessor(final Socket socket, final Protocol.ServerRequest request) {
        super(socket, request);
    }

    @Override
    public void run() {
        LOGGER.info("Submit task start processing");
    }
}