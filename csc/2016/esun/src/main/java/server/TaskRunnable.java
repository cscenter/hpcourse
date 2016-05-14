package server;

import communication.Protocol;

/**
 * Created by Helen on 13.04.16.
 */
public class TaskRunnable implements Runnable {
    Task task;
    TaskManager manager;
    public TaskRunnable(int id, TaskManager manager){
        this.manager = manager;
        this.task = manager.getTask(id);
    }
    @Override
    public void run() {
        try {
            Protocol.Task taskInfo = task.getTaskInfo();
            long a = getParam(taskInfo.getA()),
                    b = getParam(taskInfo.getB()),
                    m = getParam(taskInfo.getM()),
                    p = getParam(taskInfo.getP()),
                    n = task.getTaskInfo().getN();
            task.compute(a, b, p, m, n);
        }
        catch (InterruptedException ex){

        }
    }

    private long getParam(Protocol.Task.Param parameter) throws InterruptedException {
        if(parameter.hasValue())
            return parameter.getValue();
        else
            return manager.getTaskResult(parameter.getDependentTaskId());
    }
}
