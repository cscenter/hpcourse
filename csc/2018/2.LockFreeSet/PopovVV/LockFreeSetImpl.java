import java.util.concurrent.atomic.AtomicMarkableReference;

public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T>
{
    private final Node head = new Node();

    @Override
    public boolean add(T valueToAdd)
    {
        while (true)
        {
            Pair pair = find(valueToAdd);

            Node firstNode = pair.first;
            Node secondNode = pair.second;

            if (secondNode == null || secondNode.value.compareTo(valueToAdd) != 0)
            {
                Node addedNode = new Node(secondNode, valueToAdd);
                
                if (firstNode.next.compareAndSet(
                        secondNode,
                        addedNode,
                        false, false)
                )
                {
                    return true;
                }
            }
            else
            {
                return false;
            }
        }
    }

    @Override
    public boolean remove(T valueToRemove)
    {
        while (true)
        {
            Pair pair = find(valueToRemove);

            Node firstNode = pair.first;
            Node secondNode = pair.second;

            if (secondNode != null && secondNode.value.compareTo(valueToRemove) == 0)
            {
                Node thirdNode = secondNode.next.getReference();

                if (!secondNode.next.compareAndSet(
                    secondNode.next.getReference(),
                    secondNode.next.getReference(),
                    false, true)
                )
                {
                    continue;
                }

                firstNode.next.compareAndSet(secondNode, thirdNode, false, false);

                return true;
            }
            else
            {
                return false;
            }
        }
    }

    @Override
    public boolean contains(T valueToDetect)
    {
        Node node = head.next.getReference();

        while (node != null && node.value.compareTo(valueToDetect) < 0)
        {
            node = node.next.getReference();
        }

        return node != null &&
              !node.next.isMarked() &&
               node.value.compareTo(valueToDetect) == 0;
    }

    @Override
    public boolean isEmpty()
    {
        return head.next.getReference() == null;
    }

    private class Node
    {
        T value;
        AtomicMarkableReference<Node> next;

        Node()
        {
            value = null;
            next = new AtomicMarkableReference<Node>(null, false);
        }

        Node(Node nextNode, T nodeValue)
        {
            value = nodeValue;
            next = new AtomicMarkableReference<Node>(nextNode, false);
        }
    }

    private class Pair
    {
        Node first, second;

        Pair()
        {
            first = null;
            second = null;
        }

        Pair(Node firstNode, Node secondNode)
        {
            first = firstNode;
            second = secondNode;
        }
    }

    private Pair find(T value)
    {
        while (true)
        {
            boolean flag = true;

            Node firstNode = head;
            Node secondNode = firstNode.next.getReference();
            Node thirdNode;

            while (secondNode != null)
            {
                thirdNode = secondNode.next.getReference();

                if(secondNode.next.isMarked())
                {
                    if (firstNode.next.compareAndSet(
                            secondNode,
                            thirdNode,
                            false, false)
                    )
                    {

                        secondNode = thirdNode;
                    }
                    else
                    {
                        flag = false;
                        break;
                    }
                }
                else
                {
                    if (secondNode.value == null || secondNode.value.compareTo(value) >= 0)
                    {
                        return new Pair(firstNode, secondNode);
                    }

                    firstNode = secondNode;
                    secondNode = thirdNode;
                }
            }

            if (flag)
            {
                return new Pair(firstNode, null);
            }
        }
    }
}
