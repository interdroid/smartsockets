package smartsockets.hub.state;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import smartsockets.direct.SocketAddressSet;

public class HubList {
        
    private static int RETRY_DELAY = 15000;
        
    private final StateCounter state; 
  
    private final LinkedList connectedHubs = new LinkedList();
    
    // TODO: actually use this list! 
    private final LinkedList unConnectedHubs = new LinkedList();         
    private final LinkedList mustCheck = new LinkedList();
        
    private final HashMap map = new HashMap();    
    
    private HubDescription localDescription;
    
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
    
    public HubList(StateCounter state) { 
        this.state = state; 
    }
        
    public synchronized HubDescription nextHubToCheck() {
               
        // Wait until there are proxies to check.
        while (mustCheck.size() == 0) {            
            try { 
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        } 
        
        while (true) { 
            // Get the first one from the list. 
            HubDescription tmp = (HubDescription) mustCheck.getFirst();
            
            if (tmp.getLastContact() == 0) {
                // it's a new entry, so we can check it immediately
                return (HubDescription) mustCheck.removeFirst();                        
            }
        
            // it's an old entry, so check it we have to wait for a while. 
            long now = System.currentTimeMillis();

            if (tmp.getLastConnect()+RETRY_DELAY < now) {
                // we've passed the deadline and can return the proxy. 
                return (HubDescription) mustCheck.removeFirst();
            }
            
            long waitTime = (tmp.getLastConnect()+RETRY_DELAY) - now;
            
            try {
                wait(waitTime);
            } catch (InterruptedException e) {
                // ignore
            }
            
            // We may have reached this point because the deadline has passed, 
            // OR because we have been interrupted by a new proxy that was added 
            // to the list. We decide on what to next by running the loop again.            
        } 
    }
    
    public void addLocalDescription(HubDescription desc) {
        // NOTE: We assume that it is not required to be thread safe!        
        // The description of the local machine is only put in the map, not the
        // list...
        localDescription = desc;
        map.put(desc.hubAddress, desc);        
    }
    
    public HubDescription getLocalDescription() {
        // NOTE: We assume that it is not required to be thread safe!
        return localDescription;
    }
                  
    public synchronized boolean contains(SocketAddressSet m) {         
        return map.containsKey(m); 
    }
           
    public HubDescription get(SocketAddressSet m) {                        
        return (HubDescription) map.get(m);
    }
            
    public HubDescription get(String target) {        
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
    
    public synchronized Iterator connectedHubsIterator() { 
        
        PartialIterator result = new PartialIterator();
        
        Iterator i = connectedHubs.iterator();
        
        while (i.hasNext()) { 
            result.add(i.next());
        }
        
        i = mustCheck.iterator();
        
        while (i.hasNext()) {
            
            HubDescription p = (HubDescription) i.next(); 
            
            if (p.haveConnection()) {             
                result.add(p);
            }               
        }
        
        return result;
    }
    
    public synchronized void putBack(HubDescription d) {
        
        if (d.reachableKnown() && d.isReachable()) { 
            connectedHubs.addLast(d);        
        } else {
            // Existing entries go to the tail of the list
            mustCheck.addLast(d);
            notifyAll();
        } 
    }
    
    public synchronized HubDescription add(SocketAddressSet a) { 
        
        HubDescription tmp = get(a);
        
        if (tmp == null) {   
            tmp = new HubDescription(a, state);
            map.put(tmp.hubAddress, tmp);
            
            //System.out.println("@@@@@@@@@@@@@ ADD NEW PROXY:\n " + tmp + "\n");      
                                   
            // Fresh entries go to the head of the list
            mustCheck.addFirst(tmp);
            notifyAll();
        }
        
        return tmp;
    }
    
    public synchronized LinkedList directionToClient(String client) {

        // Collect the addresses of all proxies that claim to known this client 
        // and are reachable from our location in a single hop. For all proxies 
        // that we can only reach in multiple hops, we return the address of the
        // referring proxy instead (i.e., the 'reachable proxy' that informed us
        // of the existance of the 'unreachable proxy').   
        // 
        // We return the results in list, sorted by how 'good an option' they 
        // are. The order is as follows: 
        // 
        //  1. local proxy
        //
        //  2. proxies that can reach the client directly and, 
        //      a. we can connect to directly
        //      b. can connect directly to us
        //
        //  3. indirections for proxies that can reach the client directly, but 
        //     which we cannot reach and,  
        //      a. we can connect to directly
        //      b. can connect directly to us
        
        LinkedList good = new LinkedList();
        LinkedList bad = new LinkedList();
        LinkedList ugly = new LinkedList();
                
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            
            HubDescription tmp = (HubDescription) itt.next();
            
            if (tmp.containsClient(client)) {

                System.out.println("@@@@@@@@@@@@@ Found proxy for client: " 
                        + client + ":\n" + tmp + "\n");
                
                if (tmp == localDescription) {
                    good.addFirst(tmp.hubAddressAsString);                    
                } else if (tmp.isReachable()) {
                    good.addLast(tmp.hubAddressAsString);
                } else if (tmp.canReachMe()) { 
                    bad.addLast(tmp.hubAddressAsString);
                } else {                                         
                    HubDescription indi = tmp.getIndirection();
                    
                    if (indi != null) { 
                        if (indi.isReachable()) { 
                            ugly.addFirst(indi.hubAddressAsString);
                        } else if (indi.canReachMe()) {  
                            ugly.addLast(indi.hubAddressAsString);
                        }
                    }
                }
            }
        }
        
        good.addAll(bad);
        good.addAll(ugly);
        
        return good;
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
            
            HubDescription tmp = (HubDescription) itt.next();
            
            if (skip != null && skip.contains(tmp.hubAddress.toString())) { 
                System.out.println("@@@@@@@@@@@@@ Skipping proxy: " 
                        + tmp.hubAddress);                 
            
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
    
    public Iterator findHubsForTarget(String target, boolean includeLocal) { 
        
        PartialIterator result = new PartialIterator();
        
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            
            HubDescription tmp = (HubDescription) itt.next();
            
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
               
    public synchronized String [] hubsAsString() {
        
        int count = map.size();
        
        String [] result = new String[count]; 
        
        Iterator itt = map.values().iterator();
        
        for (int i=0;i<count;i++) { 
            result[i] = ((HubDescription) itt.next()).hubAddressAsString;                                               
        }

        return result;
    }
  
    public synchronized ArrayList allClients(String tag) {
        
        ArrayList result = new ArrayList();        
        Iterator itt = map.values().iterator();
        
        while (itt.hasNext()) { 
            HubDescription tmp = (HubDescription) itt.next();           
            result.addAll(tmp.getClients(tag));
        }
        
        return result;
    }
    
    public String toString() {
        
        StringBuffer result = new StringBuffer();
       
        result.append("Hubs with a direct connection:\n");
        
        Iterator itt = connectedHubs.iterator();
        
        while (itt.hasNext()) { 
            HubDescription desc = (HubDescription) itt.next();
            result.append(desc).append('\n');            
        }
        
        result.append("Hubs without a direct connection:\n");
        
        itt = unConnectedHubs.iterator();
        
        while (itt.hasNext()) { 
            HubDescription desc = (HubDescription) itt.next();
            result.append(desc).append('\n');            
        }
        
        result.append("Hubs which need to be checked:\n");
        
        itt = mustCheck.iterator();
        
        while (itt.hasNext()) { 
            HubDescription desc = (HubDescription) itt.next();
            result.append(desc).append('\n');            
        }
        
        return result.toString();
    }
}
