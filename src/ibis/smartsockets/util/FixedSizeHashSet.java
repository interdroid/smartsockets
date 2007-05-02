package ibis.smartsockets.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FixedSizeHashSet<K> implements Set<K> {

    private final FixedSizeHashMap<K,K> map; 
    
    public FixedSizeHashSet(int size) { 
        map = new FixedSizeHashMap<K,K>(size);
    }

    public boolean add(K o) {
        
        if (map.containsKey(o)) { 
            return false;
        }
        
        map.put(o, null);
        return true;
    }

    public boolean addAll(Collection<? extends K> c) {

        boolean changed = false;
        
        for (K k : c) { 
            boolean tmp = add(k);
            changed = (tmp || changed);
        }
        
        return changed;
    }

    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    public boolean containsAll(Collection<?> c) {
        
        for (Object o : c) {
            if (!map.containsKey(o)) { 
                return false;
            }
        }
        
        return true;
    }
    
    public Iterator<K> iterator() {
        return map.keySet().iterator();
    }

    public boolean removeAll(Collection<?> c) {
    
        boolean changed = false;
        
        for (Object o : c) {
            boolean tmp = remove(o);           
            changed = (tmp || changed);
        }
        
        return changed;    
    }

    @SuppressWarnings("unchecked")
    public boolean retainAll(Collection<?> c) {

        int before = map.size();
        
        HashSet<K> tmp = new HashSet<K>();
        
        for (Object k : c) {
            
            if (map.containsKey(k)) { 
                tmp.add((K) k);
            }            
        }

        map.clear();
        addAll(tmp);
        
        return (map.size() != before);
    }

    public Object[] toArray() {
        return map.keySet().toArray();
    }

    public <T> T[] toArray(T[] a) {
        return map.keySet().toArray(a);
    }

    public void clear() {
        map.clear();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean remove(Object o) {
        return (map.remove(o) != null);
    }

    public int size() {
        return map.size();
    }

}
