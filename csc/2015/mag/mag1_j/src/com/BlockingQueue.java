package com.cscenter;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Mark on 09.05.2015.
 */
public class BlockingQueue<E> implements CustomQueue<E> {
    private Queue<E> queue = new LinkedList<E>();
    public boolean isEmpty(){
        return queue.isEmpty();
    }
    @Override
    public synchronized void push(E e){
        queue.add(e);
        notifyAll();
    }

    @Override
    public synchronized E pop(){
        E e = null;
        while(queue.isEmpty()){
            try {
                wait();
            }catch (InterruptedException e1){
                return e;
            }
        }
        e = queue.remove();
        return e;
    }
}
