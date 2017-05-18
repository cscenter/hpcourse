package util;

/**
 * Created by nikita.sokeran@gmail.com
 */
public class Functions {
    /**
     * @return result if no arithmetic exception, otherwise return null
     */
    public static Long calculateModulo(long a, long b, long p, long m, long n) {
        try {
            while (n-- > 0) {
                b = (a * p + b) % m;
                a = b;
            }
            return a;
        } catch (ArithmeticException ex) {
            return null;
        }
    }
}
