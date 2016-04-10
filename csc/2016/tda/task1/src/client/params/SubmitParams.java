package client.params;

import static communication.Protocol.*;

/**
 * @author Dmitriy Tseyler
 */
public class SubmitParams implements MessageParams<SubmitTask.Builder> {
    private final Parameter a;
    private final Parameter b;
    private final Parameter p;
    private final Parameter m;
    private final long n;

    public SubmitParams(Parameter a, Parameter b, Parameter p, Parameter m, long n) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
    }

    @Override
    public void configure(SubmitTask.Builder message) {
        Task task = Task.newBuilder()
                .setA(getParam(a))
                .setB(getParam(b))
                .setP(getParam(p))
                .setM(getParam(m))
                .setN(n).build();
        message.setTask(task);
    }

    private static Task.Param getParam(Parameter parameter) {
        Task.Param.Builder builder = Task.Param.newBuilder();
        if (parameter.isDepends()) {
            builder.setDependentTaskId((int)parameter.getValue());
        } else {
            builder.setValue(parameter.getValue());
        }
        return builder.build();
    }
}
