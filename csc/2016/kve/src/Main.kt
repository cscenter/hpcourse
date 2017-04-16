import communication.Client
import communication.Server

fun main(args: Array<String>) {
    val server = Server(1)
    server.start()
    val client = Client()
    client.sendSubmitTask()
    client.sendSubmitTask(4, 3, 7, 100, 3)
    repeat(10) {
        client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    }
    client.sendListTask()
    repeat(4) {
        client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    }
    client.sendSubmitTaskWithDependencies(a=1, b = 2, p = 10, n = 1000)
    client.sendSubscribeTask(16)

}