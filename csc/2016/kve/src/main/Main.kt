package main

import test.Client

fun main(args: Array<String>) {
    val server = Server(1)
    server.create()
    val client = Client()
    client.sendSubmitTask()
    client.sendSubmitTask(4, 3, 7, 100, 3)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendListTask()
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubmitTask(40000, 3000000, 7000000, 1000000, 30000)
    client.sendSubscribeTask(12)

}