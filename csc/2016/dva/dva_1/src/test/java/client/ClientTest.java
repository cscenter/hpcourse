package client;

import communication.Protocol;
import org.junit.Assert;
import org.junit.Test;
import server.Server;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class ClientTest {
    static Logger logger = Logger.getLogger(ClientTest.class.getName());

    static Protocol.Task.Param.Builder simpleParam(long value) {
        return Protocol.Task.Param.newBuilder().setValue(value);
    }

    static Protocol.Task.Builder simpleTask(long a, long b, long p, long m, long n) {
        return Protocol.Task.newBuilder()
                .setA(simpleParam(a))
                .setB(simpleParam(b))
                .setP(simpleParam(p))
                .setM(simpleParam(m))
                .setN(n);
    }


    static class ClientRunnable implements Runnable {
        private final String clientId;
        private final String host;
        private final int port;

        ClientRunnable(String clientId, String host, int port) {
            this.clientId = clientId;
            this.host = host;
            this.port = port;
        }

        @Override
        public void run() {
            int requestsCount = 0;
            try {
                Client client = new Client(clientId, host, port);
                client.sendRequest(
                        Protocol.ServerRequest.newBuilder().setSubmit(
                                Protocol.SubmitTask.newBuilder()
                                        .setTask(simpleTask(100, 2, 3, 100, 100000))
                        )
                );
                requestsCount++;

                client.sendRequest(
                        Protocol.ServerRequest.newBuilder().setSubmit(
                                Protocol.SubmitTask.newBuilder()
                                        .setTask(simpleTask(200, 2, 3, 100, 200000))
                        )
                );
                requestsCount++;

                client.sendRequest(
                        Protocol.ServerRequest.newBuilder()
                                .setList(Protocol.ListTasks.getDefaultInstance())
                );
                requestsCount++;

                for (int responsesCount = 0; responsesCount < requestsCount; responsesCount++) {
                    Protocol.ServerResponse response = client.readResponse();
                    if (response == null) {
                        logger.warning("client " + clientId + " response=null");
                        break;
                    }
                    logger.info("client " + clientId + " response=" + response);

                    if (response.hasListResponse()) {
                        List<Protocol.ListTasksResponse.TaskDescription> tasksList
                                = response.getListResponse().getTasksList();
                        for (Protocol.ListTasksResponse.TaskDescription taskD : tasksList) {
                            int id = taskD.getTaskId();
                            if (!taskD.hasResult()) {
                                client.sendRequest(
                                        Protocol.ServerRequest.newBuilder()
                                                .setSubscribe(Protocol.Subscribe.newBuilder()
                                                        .setTaskId(id))
                                );
                                requestsCount++;

                                client.sendRequest(
                                        Protocol.ServerRequest.newBuilder().setSubmit(
                                                Protocol.SubmitTask.newBuilder()
                                                        .setTask(simpleTask(200, 2, 3, 100, 200000)
                                                                .setA(Protocol.Task.Param.newBuilder()
                                                                        .setDependentTaskId(id)))));
                                requestsCount++;
                            }
                        }
                    }
                }
                synchronized (client.responses) {
                    logger.info("client " + clientId
                            + ": requests count = " + requestsCount
                            + "responses count = " + client.responses.size()
                    );
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "client " + clientId, e);
            }
        }
    }

    //@Test
    public static void main(String[] args) throws IOException {
//        new Thread(() -> {
//            try {
//                Server.main(new String[]{"1234"});
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }).start();

        for (int i = 0; i < 10; i++) {
            new Thread(
                    new ClientRunnable("client" + i, "localhost", 1234)
            ).start();
        }
    }
}