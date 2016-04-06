package server.storage;

import server.thread.Calculator;

/**
 * @author Dmitriy Tseyler
 */
public class TaskWrapper {
    private final Calculator calculator;
    private final String clientId;

    TaskWrapper(Calculator calculator, String clientId) {
        this.calculator = calculator;
        this.clientId = clientId;
    }

    public Calculator getCalculator() {
        return calculator;
    }

    public String getClientId() {
        return clientId;
    }
}
