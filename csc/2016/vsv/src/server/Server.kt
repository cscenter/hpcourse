package server

import task.RequestExecutorService
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean

class Server {

    private val running = AtomicBoolean(false)
    private val executorService = RequestExecutorService()

    fun start(port: Int = 4774) {
        running.set(true)
        println("Server statring on port $port ")

        try {
            ServerSocket(port).use {
                while (running.get()) {
                    try {
                        it.accept().use {
                            executorService.execute(it)
                        }
                    } catch (e: IOException) {
                        print("Cannot accept client socket, ${e.cause}, ${e.message}")
                    }
                }
                println("Server stopped")
            }
        } catch (e: IOException) {
            print("Cannot run server socket on $port: ${e.cause}, ${e.message}")
            throw e
        }
    }

    fun stop() {
        println("Server stopping")
        running.set(false)
    }
}
