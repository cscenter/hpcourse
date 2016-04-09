package server

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class Server {

    private val running = AtomicBoolean(false)

    fun start(port: Int = 4774) {
        running.set(true)
        try {
            val serverSocket = ServerSocket(port).use {
                while (running.get()) {
                    try {
                        val clientSocket = it.accept()
                        //run the task
                    } catch (e: IOException) {
                        print("Cannot accept client socket, ${e.cause}, ${e.message}")
                    }
                }
            }
        } catch (e: IOException) {
            print("Cannot run server socket at $port: ${e.cause}, ${e.message}")
            throw e
        }
    }

    fun stop() {
        running.set(false)
    }
}
