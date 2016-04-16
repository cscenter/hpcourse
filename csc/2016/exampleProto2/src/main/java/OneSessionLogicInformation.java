import java.net.Socket;

/**
 * Created by Alex on 14.04.2016.
 */
public class OneSessionLogicInformation {
    Socket socket;
    int numOfThread;
    long keyOfTask;
    Object Monitor;
    boolean computeIsEnd;
    boolean skipCompute;

    public void setSkipCompute(boolean skipCompute) {
        this.skipCompute = skipCompute;
    }

    public void setComputeIsEnd(boolean computeIsEnd) {
        this.computeIsEnd = computeIsEnd;
    }

    public void setKeyOfTask(long keyOfTask) {
        this.keyOfTask = keyOfTask;
    }

    public void setMonitor(Object monitor) {
        Monitor = monitor;
    }

    public void setNumOfThread(int numOfThread) {
        this.numOfThread = numOfThread;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public int getNumOfThread() {
        return numOfThread;
    }

    public long getKeyOfTask() {
        return keyOfTask;
    }

    public Object getMonitor() {
        return Monitor;
    }

    public Socket getSocket() {
        return socket;
    }

    public boolean getComputeIsEnd(){
        return computeIsEnd;
    }
    public boolean getSkipCompute(){
        return skipCompute;
    }
}
