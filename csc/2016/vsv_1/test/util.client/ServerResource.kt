package util.client

import org.junit.rules.ExternalResource
import server.Server


class ServerResource(val port: Int) : ExternalResource() {

    private val myServer: Server = Server()

    override fun before() {
        Thread(Runnable {
            myServer.start(port)
        }).start()
    }

    override fun after() {
        myServer.stop()
    }
}
