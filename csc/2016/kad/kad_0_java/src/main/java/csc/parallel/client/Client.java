package csc.parallel.client;

import communication.Protocol;
import communication.Protocol.ServerRequest;
import communication.Protocol.SubmitTask;
import communication.Protocol.Task;
import communication.Protocol.Task.Param;
import communication.Protocol.WrapperMessage;
import communication.Protocol.ListTasks;
import communication.Protocol.Subscribe;
import communication.Protocol.ListTasksResponse.TaskDescription;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrey Kokorev
 *         Created on 31.03.2016.
 */
public class Client implements AutoCloseable
{
    private final Logger logger = LoggerFactory.getLogger(Client.class);

    private final InetAddress address;
    private final int port;
    private final String clientId;
    private final Socket socket;
    private AtomicLong requestsSent = new AtomicLong();
    private Thread responseListener;
    // requestId -> (lock, response)
    private final Map<Long, Pair<Object, Protocol.ServerResponse>> responses = new HashMap<>();

    public Client(String clientId, int port) throws IOException
    {
        this(clientId, null, port);
    }

    public Client(String clientId, String host, int port) throws IOException
    {
        this.clientId = clientId;
        this.port = port;
        this.address = InetAddress.getByName(host);
        this.socket = new Socket(this.address, this.port);
        startResponseListener();
    }

    private void startResponseListener()
    {
        responseListener = new Thread(() -> {
            try
            {
                while(true)
                {
                    WrapperMessage response = WrapperMessage.parseDelimitedFrom(socket.getInputStream());
                    long id = response.getResponse().getRequestId();
                    logger.trace("parsing request {}", id);

                    Object lock;
                    synchronized (responses)
                    {

                        Pair<Object, Protocol.ServerResponse> p = responses.get(id);
                        logger.trace("getting lock for {}", id);
                        lock = p.getKey();
                        responses.put(
                                id,
                                new Pair<>(lock, response.getResponse())
                        );
                    }

                    synchronized (lock)
                    {
                        lock.notifyAll();
                    }
                }

            } catch (IOException e)
            {
                //Socket closed, or smth
                //Stubs
                Protocol.ListTasksResponse.Builder lr = Protocol.ListTasksResponse.newBuilder()
                        .setStatus(Protocol.Status.ERROR);
                Protocol.SubmitTaskResponse.Builder st = Protocol.SubmitTaskResponse.newBuilder()
                        .setSubmittedTaskId(0)
                        .setStatus(Protocol.Status.ERROR);

                Protocol.SubscribeResponse.Builder sub = Protocol.SubscribeResponse.newBuilder()
                        .setStatus(Protocol.Status.ERROR);

                //have to release everybody
                synchronized (responses)
                {
                    responses.forEach((id, p) -> {
                        Protocol.ServerResponse r = Protocol.ServerResponse.newBuilder()
                                .setRequestId(0)
                                .setSubmitResponse(st)
                                .setListResponse(lr)
                                .setSubscribeResponse(sub).build();
                        Object lock = p.getKey();

                        responses.put(id, new Pair<>(lock, r));
                        synchronized (lock)
                        {
                            lock.notifyAll();
                        }
                    });
                }

            }
        });
        responseListener.setName("-- Response listener for " + this.clientId);
        responseListener.start();
    }

    /**
     * Sends task using given params, returns task id
     * @throws IOException
     */
    public Integer sendTask(Task t) throws IOException, InterruptedException
    {
        SubmitTask.Builder st = SubmitTask.newBuilder().setTask(t);

        long requestId = wrapAndSend(null, st, null);

        Protocol.ServerResponse response = waitForResponse(requestId);
        Protocol.Status status = response.getSubmitResponse().getStatus();
        if(status == Protocol.Status.ERROR)
            throw new IOException("Response status: error");

        int taskId = response.getSubmitResponse().getSubmittedTaskId();
        logger.debug("Task sent {}", taskId);
        return taskId;
    }

    public Future<Long> subscribe(int taskId)
    {
        Subscribe.Builder subscribe = Subscribe.newBuilder().setTaskId(taskId);

        long requestId = wrapAndSend(null, null, subscribe);

        return new SubscriptionFuture(getOrCreateRequestLock(requestId), requestId);
    }

    public List<TaskDescription> listTasks() throws IOException, InterruptedException
    {
        long requestId = wrapAndSend(ListTasks.newBuilder(), null, null);
        Protocol.ServerResponse response = waitForResponse(requestId);

        if(response.getListResponse().getStatus() == Protocol.Status.ERROR)
            throw new IOException("Response status: error");

        return response.getListResponse().getTasksList();
    }

    private Protocol.ServerResponse waitForResponse(long requestId) throws InterruptedException
    {
        Object lock = getOrCreateRequestLock(requestId);
        Protocol.ServerResponse response;
        synchronized (lock)
        {
            while(true)
            {
                synchronized (responses)
                {
                    response = responses.get(requestId).getValue();
                }
                if(response != null)
                    break;
                lock.wait();
            }
        }
        return response;
    }

    private Object getOrCreateRequestLock(long requestId)
    {
        Object lock = new Object();
        Pair<Object, Protocol.ServerResponse> p = new Pair<>(lock, null);
        synchronized (responses)
        {
            if(responses.containsKey(requestId))
            {
                lock = responses.get(requestId).getKey();
            }
            else
            {
                logger.trace("Creating lock for {}", requestId);
                responses.put(requestId, p);
            }
        }
        return lock;
    }

    /**
     * Sends wrapped request, returns request id.
     */
    private long wrapAndSend(ListTasks.Builder list,
                             SubmitTask.Builder submit,
                             Subscribe.Builder subscribe)
    {
        long id = requestsSent.incrementAndGet();

        ServerRequest.Builder r = ServerRequest.newBuilder()
                .setClientId(this.clientId)
                .setRequestId(id);

        if(list != null)
        {
            r.setList(list);
        } else if (submit != null)
        {
            r.setSubmit(submit);
        } else if (subscribe != null)
        {
            r.setSubscribe(subscribe);
        } else {
            logger.error("Nothing to wrap!");
        }

        // create lock before sending
        getOrCreateRequestLock(id);

        WrapperMessage w = WrapperMessage.newBuilder().setRequest(r).build();
        try
        {
            w.writeDelimitedTo(socket.getOutputStream());
        } catch (IOException e)
        {
            logger.error(e.getMessage());
        }

        return id;
    }


    @Override
    public void close()
    {
        responseListener.interrupt();

        try
        {
            socket.close();
        } catch (IOException e)
        {
            logger.error("Socket close error");
            logger.error(e.getMessage());
        }
    }

    class SubscriptionFuture implements Future<Long>
    {
        private final Object lock;
        private final long requestId;
        private boolean ready = false;
        private boolean cancelled = false;
        private Long result;

        public SubscriptionFuture(Object lock, long requestId)
        {
            this.lock = lock;
            this.requestId = requestId;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled()
        {
            return cancelled;
        }

        @Override
        public boolean isDone()
        {
            return ready;
        }

        @Override
        public Long get() throws InterruptedException, ExecutionException
        {
            if(this.isDone())
                return this.result;

            synchronized (lock)
            {
                while(true){
                    synchronized (responses)
                    {
                        Protocol.ServerResponse r = responses.get(requestId).getValue();
                        if(r != null)
                        {
                            if(r.getSubscribeResponse().getStatus() == Protocol.Status.ERROR)
                            {
                                this.cancelled = true;
                                String msg = String.format("Request %d status: error", requestId);
                                throw new ExecutionException(msg, new IOException());
                            }

                            this.result = r.getSubscribeResponse().getValue();
                            this.ready = true;
                            break;
                        }
                    }
                    lock.wait();
                }
            }

            return this.result;
        }

        @Override
        public Long get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
        {
            throw new UnsupportedOperationException();
        }
    }
}
