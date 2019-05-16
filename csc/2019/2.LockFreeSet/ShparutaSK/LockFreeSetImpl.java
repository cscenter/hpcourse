/**
 * Implementation by Sofya Shparuta
 */

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T>
{
   public Node<T> head = null;
    /**
     * Добавить ключ к множеству
     *
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если value уже существует в множестве, true если элемент был добавлен
     */
    public boolean add(T value)
    {
        while(true)
        {
            Pair pred_curr = find(value);

            Node curr = pred_curr.R;
            Node pred = pred_curr.L;

            if(pred == null || curr != null && curr.equalsTo(value))
                return false;
            else
            {
                Node node = new Node(value, curr);
                node.markedNext.set(curr, false);

            if(pred.markedNext.compareAndSet(curr, node, false, false))
                return true;
            }
        }
    }


    /**
     * Удалить ключ из множества
     *
     * Алгоритм должен быть как минимум lock-free
     *
     * @param value значение ключа
     * @return false если ключ не был найден, true если ключ успешно удален
     */
    public boolean remove(T value)
    {
        while(true)
        {
            Pair pred_curr = find(value);

            Node curr = pred_curr.R;
            Node pred = pred_curr.L;

            if(curr == null || !curr.equalsTo(value))
                return false;
            else
            {
                Node succ = curr.next();
                if(!curr.markedNext.compareAndSet(succ, succ, false, true))
                    continue;

                pred.markedNext.compareAndSet(curr, succ, false, false);
                return true;
            }
        }
    }


    Pair<Node, Node> find(T value)
    {
        if(head == null){
            Pair<Node, Node> result = new Pair<Node, Node>();
            result.L = null;
            result.R = null;
            return result;
        }

        while(true)
        {
            Node pred = head;
            Node curr = pred.next();
            Node succ = new Node(null, null);

            if(curr == null) {
                Pair<Node, Node> result = new Pair<Node, Node>();
                result.L = pred;
                result.R = curr;
                return result;
            }

            while(true)
            {
                if(curr.isNextMarkedDeleted()) {
                    if (!pred.markedNext.compareAndSet(curr, curr.next(), false, false))
                        continue;

                    curr=curr.next();
                }

                else
                {
                    if(curr.compareWith(value) != -1)
                    {
                        Pair<Node, Node> result = new Pair<Node, Node>();
                        result.L = pred;
                        result.R = curr;
                        return result;
                    }
                    pred = curr;
                    curr = curr.next();
                }
            }
        }
    }


    /**
     * Проверка наличия ключа в множестве
     *
     * Алгоритм должен быть как минимум wait-free
     *
     * @param value значение ключа
     * @return true если элемент содержится в множестве, иначе - false
     */
    public boolean contains(T value)
    {
        if(head == null)
            return false;

        Node curr = head;
        while(curr.compareWith(value) == -1)
            curr = curr.next();
        return curr.equalsTo(value) && !curr.isNextMarkedDeleted();
    }


    /**
     * Проверка множества на пустоту
     *
     * Алгоритм должен быть как минимум wait-free
     *
     * @return true если множество пусто, иначе - false
     */
    public boolean isEmpty()
    {
        return (head == null || head.markedNext.isMarked());
    }

    /**
     * Возвращает lock-free итератор для множества
     *
     * Итератор должен базироваться на концепции снапшота, см.
     * @see <a href="http://www.cs.technion.ac.il/~erez/Papers/iterators-disc13.pdf">Lock-Free Data-Structure Iterators</a>
     *
     * @return новый экземпляр итератор для множества
     */
    public java.util.Iterator<T> iterator()
    {
        return null;
    }
}
