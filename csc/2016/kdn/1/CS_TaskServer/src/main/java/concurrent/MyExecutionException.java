package concurrent;

/**
 * Created by dkorolev on 4/9/2016.
 */
public class MyExecutionException extends Exception {
    public MyExecutionException(Exception innerException) {
        super(innerException);
    }
}
