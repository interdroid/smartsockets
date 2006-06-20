package ibis.connect.gossipproxy;

//import java.io.DataInputStream;
//import java.io.DataOutputStream;
//import java.io.IOException;
import ibis.connect.virtual.VirtualSocketAddress;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

class ProxyList {
    
    private static int RETRY_DELAY = 10000;

    private final LinkedList connected = new LinkedList();     
    private final LinkedList notConnected = new LinkedList();     
    private final LinkedList unknown = new LinkedList();
        
    private final HashMap map = new HashMap();    
        
    public synchronized ProxyDescription getUnconnectedProxy() {
               
        while (true) { 
        
            long waitTime = 0;
            
            if (unknown.size() > 0) {
                return (ProxyDescription) unknown.removeFirst(); 
            }
            
            if (notConnected.size() > 0) {
                ProxyDescription d = (ProxyDescription) notConnected.getFirst();
                
                long now = System.currentTimeMillis();
                
                if (d.lastContact+RETRY_DELAY <= now) {
                    notConnected.removeFirst();
                    return d;
                } else { 
                    waitTime = (d.lastContact+RETRY_DELAY) - now; 
                }
            }
                        
            // NOTE: wakes up when a new proxy is added to unknown.  
            try { 
                wait(waitTime);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
    
    public synchronized void addLocalDescription(ProxyDescription desc) {
        // The description of the local machine is only put in the map, not the
        // list...
        map.put(desc.proxyAddress, desc);                        
    }
        
           
    public synchronized void isUnreachable(ProxyDescription d) {
        d.setUnreachable(map.size());
        
        if (!d.canReachMe()) { 
            notConnected.addLast(d);
            notifyAll();
        } 
    }
    
    public synchronized void isReachable(ProxyDescription d) {        
        d.setReachable(size());
        connected.addLast(d);
        notifyAll();
    }
    
    public synchronized void canReachMe(ProxyDescription d) {        
        d.setCanReachMe(size(), 0);
        connected.addLast(d);
        notifyAll();
    }
            
    public synchronized boolean contains(VirtualSocketAddress m) {         
        return map.containsKey(m); 
    }
    
    private void add(ProxyDescription desc) {
        
        System.err.println("ADD(" + desc.proxyAddress + ")");
        
        map.put(desc.proxyAddress, desc);
        unknown.addLast(desc);
        notifyAll();
    }
        
    private ProxyDescription get(VirtualSocketAddress m) {                        
        return (ProxyDescription) map.get(m);
    }
    /*
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
    */
    
    public synchronized int size() {
        return map.size();
    }
        
    public synchronized Iterator iterator() { 
        return map.values().iterator();
    }
    
    public synchronized Iterator connectedProxiesIterator() { 
        return connected.iterator();
    }
    
    public synchronized Iterator unconnectedProxiesIterator() { 
        return notConnected.iterator();
    }
    
    public synchronized Iterator unknownProxiesIterator() { 
        return unknown.iterator();
    }        
    
    public ProxyDescription addProxyDescription(VirtualSocketAddress a) {  
        return addProxyDescription(a, 0, null);        
    }
        
    public synchronized ProxyDescription addProxyDescription(VirtualSocketAddress a, 
            int state, VirtualSocketAddress src) { 
        
        ProxyDescription tmp = get(a);
        
        if (tmp == null) {                 
            System.err.println("GET returns null! map.size() == " +             
                    map.size()); 
            
            if (map.size() > 2) { 
                System.err.println("ERROR!!! map > 2");
                new Exception().printStackTrace(System.err);
                System.exit(1);
            }
            
            tmp = new ProxyDescription(a, src, size()+1, state);
            add(tmp);            
        }
        
        return tmp;
    }
    
    
    public String toString() {
        
        StringBuffer result = new StringBuffer();
        
        Iterator itt = iterator();
        
        while (itt.hasNext()) { 
            ProxyDescription desc = (ProxyDescription) itt.next();
            result.append(desc).append('\n');            
        }
        
        return result.toString();
    }
}
