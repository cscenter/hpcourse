/**
 * Created by andrey on 07.04.16.
 */
package server;

import communication.Protocol.Task;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerTask extends Thread {
    enum Status { INITIALIZATION, RUN, COMPLETE, ERROR }
    enum ParametersStatus { OK, ERROR } // It will ERROR in case main task not exists or its status == Status.ERROR

    private static AtomicInteger generate_id = new AtomicInteger(0);
    private final int id;

    private Status status = Status.INITIALIZATION; // initial state until RUN | Error | COMPLETE
    private ParametersStatus parametersStatus = ParametersStatus.OK; // initial parameters state

    private long a, b, p, m, n; // Parameters

    private long result;
    private Task task;
    private String clientID;

    public ServerTask(String clientID, Task task) {
        id = generate_id.getAndIncrement();

        this.clientID = clientID;
        this.task = task;
        this.n = task.getN();

        System.out.println("Task " + id + " is ready to start");
    }

    private long getParameter(Task.Param parameter) {
        if (parameter.hasDependentTaskId()) {

            if (Server.getTasks().containsKey(parameter.getDependentTaskId())) {
                ServerTask mainTask = Server.getTasks().get(parameter.getDependentTaskId()); // main task

                synchronized (mainTask) {
                    try {
                        while (mainTask.getStatus() != Status.COMPLETE && mainTask.getStatus() != Status.ERROR) {
                            System.out.println("Waiting for main task " + mainTask.getID());
                            mainTask.wait();
                        }
                    }
                    finally {
                        mainTask.notifyAll(); // report other threads

                        if (mainTask.getStatus() == Status.COMPLETE) {
                            System.out.println("Dependent parameter value: " + mainTask.getResult());
                            return mainTask.getResult();
                        }

                        parametersStatus = ParametersStatus.ERROR; // if main task has status ERROR current parameter too
                        return -1;
                    }
                }
            }
            else {
                parametersStatus = ParametersStatus.ERROR; // if main task does not exist
                return -1;
            }
        }

        return parameter.getValue();
    }


    @Override
    public void run() {
        a = getParameter(task.getA()); // getting a parameter
        if (parametersStatus == ParametersStatus.ERROR) { // if status of parameters is ERROR, we cant calculate result
            status = Status.ERROR;
            return;
        }

        if (n > 0) { // if n <= 0 we don't need other parameters for calculating
            b = getParameter(task.getB());
            p = getParameter(task.getP());
            m = getParameter(task.getM());

            if (parametersStatus == ParametersStatus.ERROR || m == 0) { // if status of parameters is ERROR, we cant calculate result
                status = Status.ERROR;
                return;
            }
        }

        status = Status.RUN; // all of necessary parameters has been initialized

        while (n-- > 0) {
            b = ((a * p + b) % m);
            a = b;
        }

        result = a;

        System.out.println("Task id = " + id + " has been completed. Result: " + result);
        status = Status.COMPLETE;
    }

    public long getResult() {
        return result;
    }

    public Status getStatus() {
        return status;
    }

    public int getID() {
        return id;
    }

    public String getClientID() {
        return clientID;
    }

    public Task getTask() {
        return task;
    }
}
