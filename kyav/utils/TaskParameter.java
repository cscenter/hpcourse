package utils;

import communication.Protocol;
/*  Класс переменных */
public class TaskParameter {
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

    public long getValue(){
        if (originTask == null) {
            return value;
        }
        return originTask.getResult();
    }

    /* преобразование в формат сообщения протокола */
    public Protocol.Task.Param toProtocolParameter(){
        Protocol.Task.Param.Builder builder = new Protocol.Task.Param.Builder();
        builder.setValue(value);
        return builder.build();
    }

    public boolean isReady() {
        if (originTask == null){
            return true;
        }
        return originTask.isDone();
    }

    public Task getOriginTask() {
        return originTask;
    }

}
