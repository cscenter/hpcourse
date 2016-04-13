import client.Client;
import communication.Protocol;
import server.Server;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_SERVER_PORT = 13333;

    /**
     * @param args
     * @throws IOException when can't open socket on {@code DEFAULT_SERVER_PORT}
     */
    public static void main(final String[] args) throws IOException {

        final Server server = new Server(DEFAULT_SERVER_PORT);
        server.start();
        final Client client = new Client(DEFAULT_HOST, DEFAULT_SERVER_PORT, "client1");
        client.submitTask(createParam(14), createParam(11), createParam(15), createParam(16), 15);
        client.submitTask(createParam(14), createParam(11), createParam(15), createParam(16), 15);
        client.submitTask(createParam(14), createParam(11), createParam(15), createParam(16), 15);
        client.submitTask(createParam(14), createParam(11), createParam(15), createParam(16), 15);
    }

    public static Protocol.Task.Param createParam(final long value) {
        return Protocol.Task.Param.newBuilder().setValue(value).build();
    }
}