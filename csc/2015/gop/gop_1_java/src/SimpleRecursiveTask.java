import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
Computes sum of numbers in a fork-join manner
 */
public class SimpleRecursiveTask implements Callable<Integer> {
    private final ExecutorService pool;
    private final List<Future<Integer>> list;
    private int left;
    private final int right;
    private final int threshold;

    @Override
    public Integer call() throws ExecutionException, InterruptedException {
        while ((right - left) > threshold) { // compute rightest part and submit the rest to pool
            int mid = (right + left) / 2;
            pool.submit(new SimpleRecursiveTask(left, left + mid, threshold, pool));
            left += mid;
        }
        Integer sum = 0;
        for (int i = left; i <= right; ++i) {
            sum += left;
        }
        for (Future<Integer> f : list) {
            sum += f.get();
        }
        return sum;
    }

    public SimpleRecursiveTask(int left, int right, int threshold, ExecutorService pool) {
        this.pool = pool;
        this.threshold = threshold;
        this.right = right;
        this.left = left;
        this.list = new ArrayList<>();
    }
}
