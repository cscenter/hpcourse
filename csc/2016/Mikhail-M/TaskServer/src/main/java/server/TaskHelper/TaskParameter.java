package server.TaskHelper;

import communication.Protocol;

class TaskParameter {

    private long value;
    private Task originTask;

    public TaskParameter(long value){
        this.value = value;
        originTask = null;
    }

    public TaskParameter(Task originTask) {
        this.value = 0;
        this.originTask = originTask;
    }

    public long getValue() {
        return value;
    }

    public boolean isReady() {
        if (originTask == null){
            return true;
        }
        return originTask.isReady();
    }

    public void waitResult() throws InterruptedException {
        if (!isReady()) {
            synchronized (originTask) {
                while (!originTask.isReady()) {
                    originTask.wait();
                }
            }
        }
    }

}