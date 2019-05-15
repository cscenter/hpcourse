package kornilova.set;

import java.util.Iterator;

public class DelegatingIterator<T extends Comparable<T>> implements Iterator<T> {
    private final Iterator<T> myDelegate;

    DelegatingIterator(Iterator<T> delegate) {
        myDelegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return myDelegate.hasNext();
    }

    @Override
    public T next() {
        return myDelegate.next();
    }
}
