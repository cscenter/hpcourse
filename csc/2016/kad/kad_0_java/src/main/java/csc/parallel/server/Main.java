package csc.parallel.server;

import java.io.IOException;

/**
 * @author Andrey Kokorev
 *         Created on 30.03.2016.
 */
public class Main
{
    public static void main(String[] args) throws IOException, InterruptedException
    {
        new Thread(new TaskManager(5555)).start();
    }
}
