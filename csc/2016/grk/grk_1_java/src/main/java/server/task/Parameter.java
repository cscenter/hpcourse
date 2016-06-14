package server.task;

/** paramenter holder */
public class Parameter {
    /** paramenter value */
    public long value;
    /** task parameter belongs*/
    public Task task;

    public Parameter(long value){
        this.value = value;
        task = null;
    }

    public Parameter(Task task) {
        this.value = 0;
        this.task = task;
    }

    public boolean isReady() {
        return task == null || task.isDone;
    }
}