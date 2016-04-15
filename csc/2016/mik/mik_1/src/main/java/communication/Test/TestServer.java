package communication.Test;

import communication.Server.TaskServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by malinovsky239 on 15.04.2016.
 */
public class TestServer {
    public static void main(String[] args) {
        try {
            new TaskServer(10918, InetAddress.getByName("localhost"));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
