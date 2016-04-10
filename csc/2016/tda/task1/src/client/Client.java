package client;


import client.params.Parameter;
import client.params.SubmitParams;
import client.params.SubscribeParams;
import server.storage.Counter;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
public class Client extends Thread {
    private static final Logger log = Logger.getLogger(Client.class.getName());

    private static final Counter COUNTER = new Counter();

    private static final long ITERATIONS = 1_000_000L;

    private static final Parameter PARAMETER_1 = new Parameter(60, false);
    private static final Parameter PARAMETER_2 = new Parameter(100, false);
    private static final Parameter PARAMETER_3 = new Parameter(20, false);
    private static final Parameter PARAMETER_4 = new Parameter(51, false);

    private static final SubmitParams SUBMIT_PARAMS_1 = new SubmitParams(PARAMETER_1, PARAMETER_2, PARAMETER_3,
            PARAMETER_4, ITERATIONS);
    private static final SubmitParams SUBMIT_PARAMS_2 = new SubmitParams(PARAMETER_2, PARAMETER_1, PARAMETER_3,
            PARAMETER_4, ITERATIONS);
    private static final SubmitParams SUBMIT_PARAMS_3 = new SubmitParams(PARAMETER_1, PARAMETER_3, PARAMETER_3,
            PARAMETER_1, ITERATIONS);
    private static final SubmitParams SUBMIT_PARAMS_4 = new SubmitParams(PARAMETER_1, PARAMETER_1,
            PARAMETER_4, PARAMETER_3, ITERATIONS);

    private static final ClientConfiguration[] CONFIGURATIONS = {
            new ClientConfiguration(0, SUBMIT_PARAMS_1, COUNTER.next(), "ClientOne"),
            new ClientConfiguration(0, SUBMIT_PARAMS_2, COUNTER.next(), "ClientTwo"),
            new ClientConfiguration(0, SUBMIT_PARAMS_3, COUNTER.next(), "ClientThree"),
            new ClientConfiguration(0, SUBMIT_PARAMS_4, COUNTER.next(), "ClientFour"),
            new ClientConfiguration(1, new SubscribeParams(1), COUNTER.next(), "Client 1 subscribe"),
            new ClientConfiguration(1, new SubscribeParams(2), COUNTER.next(), "Client 2 subscribe"),
            new ClientConfiguration(1, new SubscribeParams(3), COUNTER.next(), "Client 3 subscribe"),
            new ClientConfiguration(1, new SubscribeParams(4), COUNTER.next(), "Client 4 subscribe"),
            new ClientConfiguration(2, null, COUNTER.next(), "ClientList")};

    private final String host;
    private final int port;
    private final ClientConfiguration configuration;

    private Socket socket;

    private Client(String host, int port, ClientConfiguration configuration) {
        this.host = host;
        this.port = port;
        this.configuration = configuration;
    }

    @Override
    public void run() {
        send(configuration.create());
        waitResponse();
    }

    private void send(ServerRequest request) {
        try {
            socket = new Socket(host, port);
            socket.getOutputStream().write(request.getSerializedSize());
            request.writeTo(socket.getOutputStream());
        } catch (IOException e) {
            log.warning("Can't send request: " + e.getMessage());
        }
    }

    private void waitResponse() {
        try {
            int size = socket.getInputStream().read();
            byte buffer[] = new byte[size];
            int result = socket.getInputStream().read(buffer);
            if (result == -1) throw new IOException("Can't read response");
            ServerResponse response = ServerResponse.parseFrom(buffer);
            if (response.hasSubmitResponse() && COUNTER.current() < 14) {
                int taskId = response.getSubmitResponse().getSubmittedTaskId();
                new Client(host, port, new ClientConfiguration(0, new SubmitParams(PARAMETER_1,
                        new Parameter(taskId, true), PARAMETER_3, PARAMETER_4, ITERATIONS), COUNTER.next(), "Generated"))
                .start();
            }
            String text = configuration.toText(response);
            synchronized (System.out) {
                System.out.println("==============================");
                System.out.println(text);
                System.out.println("==============================");
            }
            socket.close();
        } catch (IOException e) {
            log.warning("Error on waiting response: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 2)
            throw new IllegalArgumentException("Must be two args: host and port");

        String host = args[0];
        int port = Integer.valueOf(args[1]);

        for (ClientConfiguration configuration : CONFIGURATIONS) {
            new Client(host, port, configuration).start();
        }
    }
}
