package csc.parallel.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Andrey Kokorev
 *         Created on 30.03.2016.
 */
public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException
    {
        TaskManager srv = new TaskManager(5555);
        Thread t = new Thread(srv, "--- Task manager");
        t.start();
        t.join();
    }
}
