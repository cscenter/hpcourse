import communication.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Server extends Thread {

    private Socket socket;
    private static int countOfRequests = 0;
    private static HashMap<Integer, SubmitTask> tableOfTasks;
    private static HashMap<Integer, Object> tableOfMutex;

    private Server(Socket socket) {
        this.socket = socket;
        setPriority(NORM_PRIORITY);
        countOfRequests++;
        start();
    }

    public static void main(String[] args) throws IOException {
        String host;
        int port;

        if(args.length >= 2){
            host = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            host = "localhost";
            port = 1000;
        }

        tableOfTasks = new HashMap<>(1000);
        tableOfMutex = new HashMap<>(1000);

        ServerSocket serverSocket = new ServerSocket(port,0, InetAddress.getByName(host));
        System.out.println("Server is started !!!");

        while (true){
            new Server(serverSocket.accept());
        }
    }

    @Override
    public void run() {
        try {
            Protocol.ServerRequest request = getRequest();

            String clientId = request.getClientId();
            long requestId = request.getRequestId();

            ArrayList<BaseTask> listOfTreads = new ArrayList<>();
            /*
            if(request.hasList()) {
                listOfTreads.add(new GetListOfTasks(socket,requestId,));
            }

            if(request.hasSubscribe()) {
                listOfTreads.add(new SubscribeTask());

            }

            if(request.hasSubmit()) {
                listOfTreads.add(new SubmitTask());
                synchronized (tableOfMutex) {
                    tableOfMutex.get((int)requestId).notifyAll();
                }
            }

            for(BaseTask task : listOfTreads) {
                task.join();
            }
            */

        } catch (IOException | InterruptedException e){
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }

    }

    private Protocol.ServerRequest getRequest() throws IOException, InterruptedException{
        int size = socket.getInputStream().read();
        byte buf[] = new byte[size];
        socket.getInputStream().read(buf);
        return Protocol.ServerRequest.parseFrom(buf);
    }
}
