import communication.Protocol;

import java.io.*;

/**
 * Created by Pavel Chursin on 15.04.2016.
 */
public class TestFileGenerator {

    //String clientID;
    static long reqID = 0;

    private static Protocol.WrapperMessage generateSubmitRequest(String clientID) {
        long a = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long b = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long p = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long m = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        //long n = (long) Math.abs(Math.random()*Long.MAX_VALUE);
        long n = (long) Math.abs(Math.random()*10000000);

        Protocol.Task t = Protocol.Task.newBuilder()
                .setA(Protocol.Task.Param.newBuilder().setValue(a).build())
                .setB(Protocol.Task.Param.newBuilder().setValue(b).build())
                .setP(Protocol.Task.Param.newBuilder().setValue(p).build())
                .setM(Protocol.Task.Param.newBuilder().setValue(m).build())
                .setN(n)
                .build();
        Protocol.SubmitTask st = Protocol.SubmitTask.newBuilder()
                .setTask(t)
                .build();
        Protocol.ServerRequest sr = Protocol.ServerRequest.newBuilder()
                .setRequestId(reqID++)
                .setClientId(clientID)
                .setSubmit(st)
                .build();
        return Protocol.WrapperMessage.newBuilder().setRequest(sr).build();
    }

    public static void main(String[] args) throws IOException {
//        OutputStream os = new FileOutputStream(new File("one_cl.txt"));
        OutputStream os = new FileOutputStream(new File("ten_cl.txt"));
        //Writer wr = new OutputStreamWriter(os);
        DataOutputStream dos = new DataOutputStream(os);
        int n = 10;
        int m = 25;
        dos.writeInt(n);
        for (int i = 0; i < n; i++) {
            m += 15;
            dos.writeInt(m);
            for(int k = 0; k < m; k++) {
                //generateSubmitRequest("client"+i).writeTo(os);
                long ap = (long) Math.abs(Math.random()*Long.MAX_VALUE);
                long bp = (long) Math.abs(Math.random()*Long.MAX_VALUE);
                long pp = (long) Math.abs(Math.random()*Long.MAX_VALUE);
                long mp = (long) Math.abs(Math.random()*Long.MAX_VALUE);
                //long n = (long) Math.abs(Math.random()*Long.MAX_VALUE);
                long np = (long) Math.abs(Math.random()*10000000);
                dos.writeLong(ap);
                dos.writeLong(bp);
                dos.writeLong(pp);
                dos.writeLong(mp);
                dos.writeLong(np);
                //os.flush();
            }
        }
        os.close();
    }
}
