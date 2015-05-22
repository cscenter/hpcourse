import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SimpleBlockingQueue<E> implements BlockingQueue<E> { // based on ArrayBlockingQueue
    private final Object[] items;

//    private volatile int count;
    private int count;

    private final Condition notEmpty;
    private final Condition notFull;
    private final ReentrantLock reentrantLock;

    @Override
    public boolean add(E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue full");
    }

    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        reentrantLock.lock();
        try {
            if (count == items.length) {
                return false;
            }
            enqueue(e);
            return true;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public E remove() {
        E x = poll();
        if (x != null) {
            return x;
        }
        throw new NoSuchElementException();
    }

    @Override
    public E poll() {
        reentrantLock.lock();
        try {
            return (count == 0) ? null : dequeue();
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public E element() {
        throw new NotImplementedException();
    }

    @Override
    public E peek() {
        throw new NotImplementedException();
    }

    @Override
    public void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        reentrantLock.lockInterruptibly();
//        reentrantLock.lock();
        try {
            while (count == items.length) {
                notFull.await();
            }
            enqueue(e);
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        throw new NotImplementedException();
    }

    private void enqueue(E x) {
        items[count++] = x;
        notEmpty.signal();
    }

    private E dequeue() {
        E x = (E) items[--count];
        notFull.signal();
        return x;
    }

    @Override
    public E take() throws InterruptedException {
        reentrantLock.lockInterruptibly();
//        reentrantLock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }
            return dequeue();
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public int remainingCapacity() {
        reentrantLock.lock();
        try {
            return items.length - count;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean remove(Object o) {
        if (o == null) {
            throw new NullPointerException();
        }
        reentrantLock.lock();
        try {
            if (count != 0) {
                for (int i = 0; i < count; ++i) {
                    if (o.equals(items[i])) {
                        --count;
                        if (i != count) {
                            items[i] = items[count]; // exchange with last element
                        }
                        notFull.signal();
                        return true;
                    }
                }
            }
            return false;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new NotImplementedException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new NotImplementedException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new NotImplementedException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new NotImplementedException();
    }

    @Override
    public void clear() {
        throw new NotImplementedException();
    }

    @Override
    public int size() {
        reentrantLock.lock();
        try {
            return count;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        reentrantLock.lock();
        try {
            return count == 0;
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public boolean contains(Object o) {
        throw new NotImplementedException();
    }

    @Override
    public Iterator<E> iterator() {
        throw new NotImplementedException();
    }

    @Override
    public Object[] toArray() {
        throw new NotImplementedException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new NotImplementedException();
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0){
            return 0;
        }
        reentrantLock.lock();
        try {
            int n = Math.min(maxElements, count);
            int j = 0;
            int i = 0;
            while (i < n) {
                E x = (E) items[j];
                c.add(x);
                items[j] = null;
                i++;
            }
            return n;
        } finally {
            reentrantLock.unlock();
        }
    }

    public SimpleBlockingQueue(int queueLimit) {
        this(queueLimit, false);
    }

    public SimpleBlockingQueue(int queueLimit, boolean fairness) {
        if (queueLimit <= 0) {
            throw new IllegalArgumentException();
        }
        count = 0;
        items = new Object[queueLimit];
        reentrantLock = new ReentrantLock(fairness);
        notEmpty = reentrantLock.newCondition();
        notFull = reentrantLock.newCondition();
    }
}
