package concurrent;

/**
 * Created by dkorolev on 4/9/2016.
 */
public class AsyncResult<T> {
    private final MyCallable<T> action;
    private Exception e;
    private T result;
    private volatile boolean isDone;

    public AsyncResult(MyCallable<T> action) {
        this.action = action;
    }

    public void setResult(T result) {
        this.result = result;
        this.isDone = true;
    }

    public void setResult(Exception e) {
        this.e = e;
        this.isDone = true;
    }

    public boolean isDone() {
        return isDone;
    }

    public T getResult() throws MyExecutionException {
        if (!isDone) {
            throw new IllegalStateException("Assumed to be called only after setting results");
        }
        if (e != null) {
            throw new MyExecutionException(e);
        }

        return result;
    }

    public MyCallable<T> getAction() {
        return action;
    }
}
