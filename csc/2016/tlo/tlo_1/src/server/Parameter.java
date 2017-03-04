package server;

import communication.Protocol.Task.Param;

/**
 * Created by lt on 11.05.16.
 */
public class Parameter {

    private long value;
    private Param taskParam;
    private ParamStatus paramStatus;

    public Parameter(Param param) {
        taskParam = param;
    }

    public void waitAndSetParameter() {
        if (taskParam.hasDependentTaskId()) {
            if (Server.tasks.containsKey(taskParam.getDependentTaskId())) {
                MyTask task = Server.tasks.get(taskParam.getDependentTaskId());
                synchronized (task) {
                    try {
                        if (task.getTaskStatus() == MyTask.Status.ERROR) {
                            paramStatus = ParamStatus.ERROR;
                        }

                        while (task.getTaskStatus() != MyTask.Status.COMPLETED) {
                            task.wait();
                        }
                        if (task.getTaskStatus() == MyTask.Status.COMPLETED) {
                            value = taskParam.getValue();
                            paramStatus = ParamStatus.READY;
                        }
                        paramStatus = ParamStatus.ERROR;
                    } catch (InterruptedException e) {
                        e.getStackTrace();
                    } finally {
                        task.notifyAll();
                    }
                }
            }
        } else {
            value = taskParam.getValue();
            paramStatus = paramStatus.READY;
        }
    }

    public long getValue() {
        return value;
    }

    public ParamStatus getStatus() {
        return paramStatus;
    }

    public enum ParamStatus {
        READY, ERROR, RUNNING
    }


}
