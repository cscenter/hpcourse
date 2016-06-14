import communication.Protocol;

import java.io.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Pavel Chursin on 15.04.2016.
 */
public class TestFileReader {
    public static void main(String[] args) throws IOException {
//        InputStream is = new FileInputStream(new File("one_cl.txt"));
        InputStream is = new FileInputStream(new File("ten_cl.txt"));
        DataInputStream dis = new DataInputStream(is);
        AtomicInteger reqID = new AtomicInteger();
        ArrayDeque<TestClient> clients = new ArrayDeque<>();
        int n = dis.readInt();
        for (int i = 0; i < n; i++) {
            int m = dis.readInt();
            TestClient c = new TestClient("client" + i, reqID);
            clients.add(c);
            for (int k = 0; k < m; k++) {
                //Protocol.WrapperMessage wm = Protocol.WrapperMessage.parseFrom(is);
                long ap = dis.readLong();
                long bp = dis.readLong();
                long pp = dis.readLong();
                long mp = dis.readLong();
                long np = dis.readLong();
                Protocol.Task t = Protocol.Task.newBuilder()
                        .setA(Protocol.Task.Param.newBuilder().setValue(ap).build())
                        .setB(Protocol.Task.Param.newBuilder().setValue(bp).build())
                        .setP(Protocol.Task.Param.newBuilder().setValue(pp).build())
                        .setM(Protocol.Task.Param.newBuilder().setValue(mp).build())
                        .setN(np)
                        .build();
                Protocol.SubmitTask st = Protocol.SubmitTask.newBuilder()
                        .setTask(t)
                        .build();
                Protocol.ServerRequest sr = Protocol.ServerRequest.newBuilder()
                        .setRequestId(reqID.incrementAndGet())
                        .setClientId("client" + i)
                        .setSubmit(st)
                        .build();
                c.addTask(Protocol.WrapperMessage.newBuilder().setRequest(sr).build());

            }
        }

        long time = System.currentTimeMillis();

        for (TestClient c : clients)
            c.start();
        for (TestClient c : clients)
            try {
                c.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        time = System.currentTimeMillis() - time;
        System.out.println("Server took " + time + "ms to solve all tasks!");
    }
}
