import org.apache.log4j.Logger;

/**
 * Created by olgaoskina
 * 02 May 2015
 */
public class LogWrapper {
    private static Logger logger = Logger.getLogger(LogWrapper.class);

    public static void w(String message) {
        logger.warn(message);
    }

    public static void w(String message, Throwable e) {
        logger.warn(message, e);
    }

    public static void i(String message) {
        logger.info(message);
    }

    public static void i(String message, Throwable e) {
        logger.info(message, e);
    }

    public static void d(String message) {
        logger.debug(message);
    }

    public static void d(String message, Throwable e) {
        logger.debug(message, e);
    }

    public static void e(String message) {
        logger.error(message);
    }

    public static void e(Throwable e) {
        logger.error("", e);
    }

    public static void e(String message, Throwable e) {
        logger.error(message, e);
    }
}
