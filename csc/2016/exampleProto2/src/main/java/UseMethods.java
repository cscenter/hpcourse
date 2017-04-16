import java.util.HashMap;

/**
 * Created by Alex on 09.04.2016.
 */
public class UseMethods {
    static void showHashMap(HashMap<Long, OneSession> map){
        for (Long l : map.keySet()) {
            System.out.print(l+" ");
        }
    }
}
