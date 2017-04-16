package ru.csc.roman_fedorov;

/**
 * Created by roman on 27.04.2016.
 */
public class CustomTaskDescription {
    Param a;
    Param b;
    Param p;
    Param m;
    long n;
    private String clientId;
    private long result;

    public CustomTaskDescription(Param a, Param b, Param p, Param m, long n, String clientId, long result) {
        this.a = a;
        this.b = b;
        this.p = p;
        this.m = m;
        this.n = n;
        this.clientId = clientId;
        this.result = result;
    }

    public CustomTaskDescription(long data[], String clientId, long result) {
        this(new Param(data[0]), new Param(data[1]), new Param(data[2]), new Param(data[3]), data[4], clientId, result);
    }

    public Param getA() {
        return a;
    }

    public void setA(Param a) {
        this.a = a;
    }

    public Param getB() {
        return b;
    }

    public void setB(Param b) {
        this.b = b;
    }

    public Param getP() {
        return p;
    }

    public void setP(Param p) {
        this.p = p;
    }

    public Param getM() {
        return m;
    }

    public void setM(Param m) {
        this.m = m;
    }

    public long getN() {
        return n;
    }

    public void setN(long n) {
        this.n = n;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getResult() {
        return result;
    }

    public void setResult(long result) {
        this.result = result;
    }

    static class Param {
        public boolean hasValue;
        public long value;
        public int dependentTaskId;

        public Param(boolean hasValue, long value, int dependentTaskId) {
            this.hasValue = hasValue;
            this.value = value;
            this.dependentTaskId = dependentTaskId;
        }

        public Param(long value) {
            this.hasValue = true;
            this.dependentTaskId = -1;
            this.value = value;
        }
    }
}

