import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicMarkableReference;
/**
 *
 * @author nick
 */
public class LockFreeSetImpl<T extends Comparable<T>> implements LockFreeSet<T >{
     final Node<T> head=new Node<>(new AtomicMarkableReference<>(null,false),null);
     
     public Map.Entry<AtomicMarkableReference<Node<T>>,Node<T>> find(final T value){
         Node<T> iter=head;
         
         final boolean[] mark=new boolean[1];
         final boolean[] nmark=new boolean[1];
         
         while(true){
             
             AtomicMarkableReference<Node<T>> nextRef=iter.next;
             Node<T> nextNode=nextRef.get(mark);
             
             if(nextNode==null)
                 return new AbstractMap.SimpleImmutableEntry<>(nextRef,nextNode);
             
             if(mark[0]){
                  Node newNext=nextNode.next.get(nmark);
                 if(nextRef.compareAndSet(nextNode, newNext, true, mark[0]))
                     continue;
             }
             
             if(nextNode.val.compareTo(value)>=0)
                 return new AbstractMap.SimpleImmutableEntry<>(nextRef,nextNode);
             
             iter=nextNode;
         }
     }
    @Override
    public boolean add(T value){
        while(true){
            
            Map.Entry<AtomicMarkableReference<Node<T>>,Node<T>> searchResult=find(value);
            AtomicMarkableReference<Node<T>> ref=searchResult.getKey();
            Node<T> node=searchResult.getValue();
            
            if(node==null){  
                Node<T> newNode=new Node<>(new AtomicMarkableReference<Node<T>>(null,false),value);
                if(ref.compareAndSet(null, newNode,false,false))
                    return true;
                else
                    continue;
            }
            
            if(node.val.compareTo(value)==0)
                return false;
            
            Node<T> newNode=new Node<>(node.next,value);
            if(ref.compareAndSet(node, newNode,false,false))
                return true;
        }
        
    }
    @Override
    public boolean remove(T value){
        final boolean[] mark=new boolean[1];
        while(true){
            Map.Entry<AtomicMarkableReference<Node<T>>,Node<T>> searchResult=find(value);
            AtomicMarkableReference<Node<T>> ref=searchResult.getKey();
            Node<T> node=searchResult.getValue();
            
            if(node==null)
                return false;
            
             if(ref.attemptMark(node, true)){
                 Node next=node.next.get(mark);
                 if(ref.compareAndSet(node, next, true, mark[0]))
                     return true;
             }
             
        }
     }
     @Override
     public boolean contains(T value){
           Node<T> iter=head;
           final boolean[] mark=new boolean[1];
        while(true){
            Node<T> next=iter.next.get(mark);
            if(next==null)
                return false;
            if(!mark[0]&&next.val.compareTo(value)==0)
                return true;
            if(next.val.compareTo(value)>0)
                return false;
            iter=next;
        }
     }
    @Override
     public boolean isEmpty(){
              return head.next.getReference()==null;
     }
}

class Node<T extends Comparable<T> >{
    AtomicMarkableReference<Node<T>> next=null;
    T val;
    Node(AtomicMarkableReference<Node<T>> next,T val){
        this.next=next;
        this.val=val;
    }
    
}
