package ibis.connect.gossipproxy;

//import java.io.DataInputStream;
//import java.io.DataOutputStream;
//import java.io.IOException;
import ibis.connect.virtual.VirtualSocketAddress;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

class ProxyList {
    
    //private final MachineDescription myMachine;
    
    private LinkedList list = new LinkedList();     
    private HashMap map = new HashMap();    
    private boolean change = false;
            
    ProxyList(ProxyDescription local) {  
        // The local machine is special. It's in the map (so we can find it), 
        // but it's not on the list.         
        map.put(local.proxyAddress, local);
    }
                           
    synchronized void add(ProxyDescription desc) {        
        list.addLast(desc);
        map.put(desc.proxyAddress, desc);                        
        notifyAll();
        change = true;
    }
    
    synchronized void addIfNotPresent(ProxyDescription desc) {
        if (!map.containsKey(desc.proxyAddress)) { 
            add(desc);
        }        
    }
            
    synchronized boolean contains(VirtualSocketAddress m) {         
        return map.containsKey(m); 
    }
    
    synchronized ProxyDescription get(VirtualSocketAddress m) {                        
        return (ProxyDescription) map.get(m);
    }
    
    synchronized ProxyDescription get(VirtualSocketAddress m, long timeout) {
        
        ProxyDescription result = (ProxyDescription) map.get(m);
        
        long start = System.currentTimeMillis();
        
        while (result == null) { 

            // non blocking version            
            if (timeout < 0) {
                return null;
            }
            
            // blocking version
            if (timeout == 0) { 
                try { 
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            } else { 
                long waitTime = timeout - (System.currentTimeMillis()-start);
                
                if (waitTime <= 0) { 
                    return null;
                } else { 
                    try { 
                        wait(waitTime);
                    } catch (InterruptedException e) {
                        // ignore
                    }   
                }
            }

            result = (ProxyDescription) map.get(m);            
        }
        
        return result;
    }
    
    
    synchronized int size() {
        return list.size();
    }
    
    synchronized boolean anyChange() {
        boolean prev = change;
        change = false;
        return prev;
    }
    
    Iterator iterator() { 
        return list.iterator();
    }
        
    synchronized ProxyDescription removeFirst() { 
        
        while (list.size() == 0) {
            try { 
                wait();
            } catch (InterruptedException e) { 
                // ignore
            }                        
        }
                
        ProxyDescription desc = (ProxyDescription) list.removeFirst();        
        map.remove(desc.proxyAddress);        
        change = true;        
        return desc;
    }
   
    synchronized ProxyDescription getFirst() { 
        
        while (list.size() == 0) {
            try { 
                wait();
            } catch (InterruptedException e) { 
                // ignore
            }                        
        }
                
        ProxyDescription desc = (ProxyDescription) list.getFirst();        
        return desc;
    }
       
    public String toString() {
        
        StringBuffer result = new StringBuffer();
        
        Iterator itt = list.iterator();
        
        while (itt.hasNext()) { 
            ProxyDescription desc = (ProxyDescription) itt.next();
            result.append(desc).append('\n');            
        }
        
        return result.toString();
    }
}
