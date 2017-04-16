package first;


import java.io.IOException;
import java.net.Socket;

import static first.Protocol.ServerRequest;
import static first.Protocol.ServerResponse;
import static first.Protocol.Subscribe;


abstract public class ClientRequest {
    
    protected Socket socket;
    protected long requestId;
    protected String clientId;
        
    public void send() {
        ServerRequest.Builder sb = ServerRequest.newBuilder()
                                    .setRequestId(requestId)
                                    .setClientId(clientId);
        setOther(sb);
        
        try {
            writeRequest(sb.build());
        } catch (IOException e) {
            System.out.println("Cannot send request");
        }
    }
    
    public void recieve() {
        ServerResponse res = null;
        try {
            int size = (int) socket.getInputStream().read();
            if (size >= 0) {
                byte[] data = new byte[size];
                socket.getInputStream().read(data);
                res = ServerResponse.parseFrom(data);
                System.out.println("Response size = " + res.getSerializedSize());
            }
            } catch (IOException e) {
                System.out.println("Cannot read response");
            }
            //return res;
    }
    
    private void writeRequest(ServerRequest request) throws IOException {    
        socket.getOutputStream().write(request.getSerializedSize());
        System.out.println("Request size = " + request.getSerializedSize());
        request.writeTo(socket.getOutputStream());
    }

    protected abstract void setOther(ServerRequest.Builder sb);


    public static void main(String[] args) throws IOException {
        int port = 0;
        String host;
        try {
            port = Integer.parseInt(args[0]);
        } catch(NumberFormatException | IndexOutOfBoundsException e) {
            System.out.println("First argument must be number of port");
            System.exit(0);
        }
        if (args.length >= 2) {
            host = args[1];
        } else {
            host = "localhost";
        }
        
        ClientRequest client;
        Socket socket;
        
        socket = new Socket(host, port);
        client = new ClientSubmitRequest(socket, "Mr Smit", 1l);
        client.send();
        client.recieve();
        
        socket = new Socket(host, port);
        client = new ClientSubscribeRequest(socket, "Mr Smit",2l, 1);
        client.send();
        client.recieve();

        socket = new Socket(host, port);
        client = new ClientSubscribeRequest(socket, "Mr Smit",2l, 5);
        client.send();
        client.recieve();

        socket = new Socket(host, port);
        client = new ClientListRequest(socket, "Mr Smit",3l);
        client.send();
        client.recieve();
    }
}