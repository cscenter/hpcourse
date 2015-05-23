package ru.bronti.hpcource.hw1

import java.lang.ThreadGroup

public class MyThread(val task: MyThreadPool.MyWorker): Thread(task) {
}