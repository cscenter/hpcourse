package server.thread;

import communication.Protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;


/** * Parent for all task types. very nice architecture ^^ */
public abstract class AbstractTaskThread implements Runnable {
    /** connection socket */
    protected Socket socket;
    /** The Server request */
    protected Protocol.ServerRequest serverRequest;

    /**
     * Useless javadoc for constructor
     *
     * @param socket        the socket
     * @param serverRequest the server request
     */
    public AbstractTaskThread(Socket socket, Protocol.ServerRequest serverRequest){
        this.socket = socket;
        this.serverRequest = serverRequest;
    }

    /**  Send responce to client
     * @param serverResponse the server response
     * @throws IOException the io exception
     */
    protected  void send(Protocol.ServerResponse serverResponse) throws IOException {
        OutputStream out = socket.getOutputStream();
        synchronized (out){
            out.write(serverResponse.getSerializedSize());
            serverResponse.writeTo(out);
            out.flush();
        }

    }

    /**
     * Wrappper for sent function for avoid
     * unnessesary try-catch blocks in code
     * @param serverResponseBuilder the server response builder
     */
    protected void trySendResponse(Protocol.ServerResponse.Builder serverResponseBuilder) {
        try {
            send(serverResponseBuilder.build());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

}