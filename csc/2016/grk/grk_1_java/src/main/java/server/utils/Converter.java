package server.utils;

import communication.Protocol;
import server.task.Parameter;
import server.task.Task;
import server.task.TaskHolder;

/** just util class */
public class Converter {

    public static Protocol.Task.Param convertToProtocolParameter(Parameter parameter) {
        Protocol.Task.Param.Builder builder = Protocol.Task.Param.newBuilder();
        builder.setValue(parameter.value);
        return builder.build();
    }

    public static Protocol.Task convertToProtocolTask(Task task) {
        Protocol.Task.Builder builder = Protocol.Task.newBuilder();
        builder.setA(convertToProtocolParameter(task.a));
        builder.setB(convertToProtocolParameter(task.b));
        builder.setP(convertToProtocolParameter(task.p));
        builder.setM(convertToProtocolParameter(task.m));
        builder.setN(task.n.value);
        return builder.build();
    }

    public static Parameter convertToParameter(Protocol.Task.Param parameter) {
        if (parameter.hasValue()) {
            return new Parameter(parameter.getValue());
        } else {
            Task task = TaskHolder.getById(parameter.getDependentTaskId());
            if (task == null) {
                throw new ConverterException("task is null");
            }
            return new Parameter(task);
        }
    }

    public static class ConverterException extends RuntimeException {
        public ConverterException(String message) {
            super(message);
        }
    }
}
