package ibis.connect.gossipproxy;

import ibis.connect.direct.SocketAddressSet;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

public class ProxyList {
        
    private static int RETRY_DELAY = 10000;
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ProxyList.class.getName());
    
    private final StateCounter state; 
    
    private final LinkedList checked = new LinkedList();     
    private final LinkedList mustCheck = new LinkedList();
        
    private final HashMap map = new HashMap();    
    
    private ProxyDescription localDescription;
    
    private class PartialIterator implements Iterator {

        private LinkedList elements = null; 

        void add(Object o) { 
            if (elements == null) { 
                elements = new LinkedList();
            }
            elements.add(o);
        }
        
        public boolean hasNext() {
            return (elements != null && elements.size() > 0);
        }

        public Object next() {
            return (elements == null ? null : elements.removeFirst());            
        }

        public void remove() {
            throw new UnsupportedOperationException("ProxyList.remove not supported!");
        } 
    }
    
    public ProxyList(StateCounter state) { 
        this.state = state; 
    }
        
    public synchronized ProxyDescription nextProxyToCheck() {
               
        // Wait until there are proxies to check.
        while (mustCheck.size() == 0) {            
            try { 
                System.out.println("@@@@@@@@@@@@@ waiting");
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        } 
        
        while (true) { 

            System.out.println("@@@@@@@@@@@@@ get proxy");
            
            // Get the first one from the list. 
            ProxyDescription tmp = (ProxyDescription) mustCheck.getFirst();
            
            if (tmp.getLastContact() == 0) {
                
                System.out.println("@@@@@@@@@@@@@ return new");
                
                // it's a new entry, so we can check it immediately
                return (ProxyDescription) mustCheck.removeFirst();                        
            }
        
            // it's an old entry, so check it we have to wait for a while. 
            long now = System.currentTimeMillis();

            if (tmp.getLastConnect()+RETRY_DELAY < now) {

                System.out.println("@@@@@@@@@@@@@ return old");
                
                // we've passed the deadline and can return the proxy. 
                return (ProxyDescription) mustCheck.removeFirst();
            }
            
            long waitTime = (tmp.getLastConnect()+RETRY_DELAY) - now;
            
            try {
                System.out.println("@@@@@@@@@@@@@ old wait " + waitTime);                
                wait(waitTime);
            } catch (InterruptedException e) {
                // ignore
            }
            
            // We may have reached this point because the deadline has passed, 
            // OR because we have been interrupted by a new proxy that was added 
            // to the list. We decide on what to next by running the loop again.            
        } 
    }
    
    public void addLocalDescription(ProxyDescription desc) {
        // NOTE: We assume that it is not required to be thread safe!        
        // The description of the local machine is only put in the map, not the
        // list...
        localDescription = desc;
        map.put(desc.proxyAddress, desc);        
    }
    
    public ProxyDescription getLocalDescription() {
        // NOTE: We assume that it is not required to be thread safe!
        return localDescription;
    }
                  
    public synchronized boolean contains(SocketAddressSet m) {         
        return map.containsKey(m); 
    }
           
    public ProxyDescription get(SocketAddressSet m) {                        
        return (ProxyDescription) map.get(m);
    }
            
    public ProxyDescription get(String target) {        
        // TODO: not very efficient ....        
        try {
            return get(new SocketAddressSet(target));
        } catch (UnknownHostException e) {
            return null;
        }
    }
    
    
    public synchronized Iterator iterator() { 
        return map.values().iterator();
    }
    
    public synchronized Iterator connectedProxiesIterator() { 
        return checked.iterator();
    }
    
    public synchronized void putBack(ProxyDescription d) {
        
        if (d.reachableKnown()) { 
            checked.addLast(d);        
        } else {
            // Existing entries go to the tail of the list
            mustCheck.addLast(d);
            notifyAll();
        } 
    }
    
    public synchronized ProxyDescription add(SocketAddressSet a) { 
        
        ProxyDescription tmp = get(a);
        
        if (tmp == null) {   
            tmp = new ProxyDescription(a, state);
            map.put(tmp.proxyAddress, tmp);
            
            System.out.println("@@@@@@@@@@@@@ ADD NEW PROXY:\n " + tmp + "\n");      
                                   
            // Fresh entries go to the head of the list
            mustCheck.addFirst(tmp);
            notifyAll();
        }
        
        return tmp;
    }
    
    public LinkedList findClient(String client) {
        return findClient(client, null);
    }

    public synchronized LinkedList findClient(String client, List skip) {

        // Finds all proxies that claim to known this client. Return them in 
        // a list, sorted by how 'good an option' they are. We prefer proxies 
        // that we can connect to directly, followed by proxies that can connect 
        // to us directly. Finally, we also accept proxies that we cannot create 
        // a connection to in either direction.                
        LinkedList good = new LinkedList();
        LinkedList bad = new LinkedList();
        LinkedList ugly = new LinkedList();
                
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            
            ProxyDescription tmp = (ProxyDescription) itt.next();
            
            if (skip != null && skip.contains(tmp.proxyAddress.toString())) { 
                System.out.println("@@@@@@@@@@@@@ Skipping proxy: " 
                        + tmp.proxyAddress);                 
            
            } else if (tmp.containsClient(client)) {

                System.out.println("@@@@@@@@@@@@@ Found proxy for client: " 
                        + client + ":\n" + tmp + "\n");
                
                if (tmp == localDescription) {
                    good.addFirst(tmp);                    
                } else if (tmp.isReachable()) {
                    good.addLast(tmp);
                } else if (tmp.canReachMe()) { 
                    bad.addLast(tmp);
                } else {                     
                    ugly.addLast(tmp);
                }
            }
        }
        
        good.addAll(bad);
        good.addAll(ugly);
        
        return good;
    }
    
    public Iterator findProxiesForTarget(String target, boolean includeLocal) { 
        
        PartialIterator result = new PartialIterator();
        
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            
            ProxyDescription tmp = (ProxyDescription) itt.next();
            
            if (tmp.containsClient(target)) {

                // Alway add remote proxies, but only add the local one if 
                // specified!
                if (!tmp.isLocal() || includeLocal) { 
                    result.add(tmp);
                } 
            } 
        }
        
        return result;
    }
               
    public synchronized String [] proxiesAsString() {
        
        int proxies = map.size();
        
        String [] result = new String[proxies]; 
        
        Iterator itt = map.values().iterator();
        
        for (int i=0;i<proxies;i++) { 
            result[i] = ((ProxyDescription) itt.next()).proxyAddressAsString;                                               
        }

        return result;
    }

    public synchronized String[] localClientsAsString() {       
        ArrayList tmp = localDescription.getClients();      
        return (String []) tmp.toArray(new String[tmp.size()]);
    }

    public synchronized String[] clientsAsString() {
        
        TreeSet result = new TreeSet();        
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            ProxyDescription tmp = (ProxyDescription) itt.next();           
            result.addAll(tmp.getClients());
        }
        
        return (String []) result.toArray(new String[result.size()]);
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
