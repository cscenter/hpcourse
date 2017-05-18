package server;

import communication.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

public class Worker extends Thread {
    private Socket socket;
    private LinkedList<Future> futures = new LinkedList<Future>();
    private volatile boolean requestsFinished;
    private int requestCount;
    private int responseCount;
    private final Object monitor = new Object();

    public Worker(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            OutputStream out = socket.getOutputStream();
            new ResponseSender(out).start();

            InputStream in = socket.getInputStream();
            Protocol.WrapperMessage wrapperMessage = null;
            while ((wrapperMessage = Protocol.WrapperMessage.parseDelimitedFrom(in)) != null) {
                addFuture(Executor.getInstance().submit(wrapperMessage, monitor));
                requestCount++;
            }
            requestsFinished = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void addFuture(Future future) {
        futures.add(future);
    }

    private class ResponseSender extends Thread {
        private final OutputStream out;

        public ResponseSender(OutputStream out) {
            this.out = out;
        }

        @Override
        public void run() {
            while (!requestsFinished || requestCount != responseCount) {
                try {
                    synchronized (monitor) {
                        monitor.wait();
                        ArrayList<Protocol.WrapperMessage> results = processFutures();
                        sendResponses(results);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private ArrayList<Protocol.WrapperMessage> processFutures() {
                ArrayList<Protocol.WrapperMessage> results = new ArrayList<Protocol.WrapperMessage>();
                ListIterator<Future> iterator = futures.listIterator();
                while (iterator.hasNext()) {
                    Future future = iterator.next();
                    if (future.isDone()) {
                        results.add(future.getMessage());
                        iterator.remove();
                    }
                }
                return results;
        }

        private void sendResponses(ArrayList<Protocol.WrapperMessage> results) {
            try {
                for (Protocol.WrapperMessage responseMessage : results) {
                    responseMessage.writeDelimitedTo(out);
                    responseCount++;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
