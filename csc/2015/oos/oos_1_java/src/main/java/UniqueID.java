import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by olgaoskina
 * 03 May 2015
 */
public class UniqueID {
    private static AtomicLong idCounter = new AtomicLong(1);

    public static long createID() {
        return idCounter.getAndIncrement();
    }
}
