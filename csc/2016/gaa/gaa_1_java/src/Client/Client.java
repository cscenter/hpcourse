package Client;

import communication.Protocol;
import communication.Protocol.ServerRequest;
import communication.Protocol.ServerResponse;
import communication.Protocol.Task;

import java.io.IOException;
import java.net.Socket;

import static communication.Protocol.*;
import static communication.Protocol.Task.*;

/**
 * Created by scorpion on 15.04.16.
 */
public class Client extends Thread {

    Socket clSocket;
    int requets;

    public Client(int port) throws IOException {
        clSocket = new Socket("localhost", port);
        requets = 0;
    }

    @Override
    public void run() {
        new Thread() {
            @Override
            public void run() {
                try {
                    int size = 0;
                    while (true) {
                        size = clSocket.getInputStream().read();
                        byte buffer[] = new byte[size];
                        clSocket.getInputStream().read(buffer);
                        ServerResponse response = ServerResponse.parseFrom(buffer);
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        clSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
        try {
            submitTask(0, 4, 0, 1000, 1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            submitTask(0, 0, 10, 100, 100);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            submitTask(0, 1, 0, 2, 100);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            subsT(1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            subsT(2);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            listR();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            subsT(3);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            listR();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(ServerRequest message) throws IOException {
        synchronized (clSocket) {
            message.writeDelimitedTo(clSocket.getOutputStream());
        }
    }

    Param buildParam(long value) {
        return Param.newBuilder().setValue(value).build();
    }

    void submitTask(long a, long b, long p, long m, long n) throws IOException {
        Param pa = buildParam(a);
        Param pb = buildParam(b);
        Param pp = buildParam(p);
        Param pm = buildParam(m);
        Task task = Task.newBuilder().setA(pa).setB(pb).setM(pm).setP(pp).setN(n).build();
        SubmitTask submitTask = SubmitTask.newBuilder().setTask(task).build();
        sendMessage(ServerRequest.newBuilder().setSubmit(submitTask).setClientId("test").setRequestId(++requets).build());
    }

    void subsT(int id) throws IOException {
        sendMessage(ServerRequest.newBuilder().setClientId("test").setRequestId(++requets).
                setSubscribe(Subscribe.newBuilder().setTaskId(id).build()).build());
    }

    void listR() throws IOException {
        sendMessage(ServerRequest.newBuilder().setRequestId(++requets).setClientId("test").setList(
                ListTasks.newBuilder().build()
        ).build());
    }
}
