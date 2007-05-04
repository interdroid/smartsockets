package ibis.smartsockets.hub.state;

import ibis.smartsockets.direct.DirectSocketAddress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;


public class HubList {
        
    private static int RETRY_DELAY = 15000;
        
    private final StateCounter state; 
  
    private final LinkedList<HubDescription> connectedHubs = 
        new LinkedList<HubDescription>();
        
    private final LinkedList<HubDescription> mustCheck = 
        new LinkedList<HubDescription>();
    
    // TODO: do actually use this list ? 
    /*
    private final LinkedList<HubDescription> unConnectedHubs = 
        new LinkedList<HubDescription>();
    */
    
    private final HashMap<DirectSocketAddress, HubDescription> map = 
        new HashMap<DirectSocketAddress, HubDescription>();    
    
    private HubDescription localDescription;
    
    public HubList(StateCounter state) { 
        this.state = state; 
    }
        
    public synchronized HubDescription nextHubToCheck() {
               
        // Wait until there are proxies to check.
        while (mustCheck.size() == 0) {             
            try {
                wait();
            } catch (InterruptedException e) {
                // Hub shutting down ? 
                return null;
            }
        } 
        
        while (true) { 
            // Get the first one from the list. 
            HubDescription tmp = mustCheck.getFirst();
            
            if (tmp.getLastContact() == 0) {
                // it's a new entry, so we can check it immediately
                return mustCheck.removeFirst();                        
            }
        
            // it's an old entry, so check it we have to wait for a while. 
            long now = System.currentTimeMillis();

            if (tmp.getLastConnect()+RETRY_DELAY < now) {
                // we've passed the deadline and can return the hub. 
                return (HubDescription) mustCheck.removeFirst();
            }
            
            long waitTime = (tmp.getLastConnect()+RETRY_DELAY) - now;            
            
            try {
                wait(waitTime);
            } catch (InterruptedException e) {
                // Hub shutting down ? 
                return null;
            }
            
            // We may have reached this point because the deadline has passed, 
            // OR because we have been interrupted by a new hub that was added 
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
        // NOTE: We assume that it is not required to be thread safe since the 
        // local description reference is constant.   
        return localDescription;
    }
                  
    public synchronized boolean contains(DirectSocketAddress m) {         
        return map.containsKey(m); 
    }
           
    public synchronized HubDescription get(DirectSocketAddress m) {                        
        return map.get(m);
    }
            
    public synchronized void select(Selector s) {
        
        boolean all = s.needAll();
        boolean connected = s.needConnected();
        boolean local = s.needLocal();

        if (local && !all && !connected) { 
            // shortcut
            s.select(localDescription);
            return;
        }
                
        Iterator i = map.values().iterator();
            
        while (i.hasNext()) {
            
            HubDescription d = (HubDescription) i.next();
                
            if (all || 
               (connected && (d.getConnection() != null)) ||  
               (local && d.local)) { 
                s.select(d);
            }            
        } 
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
    
    public synchronized HubDescription add(DirectSocketAddress a) { 
        
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
    
    public synchronized DirectSocketAddress[] knownHubs() {
        
        ArrayList<DirectSocketAddress> l = new ArrayList<DirectSocketAddress>();
        
        l.add(localDescription.hubAddress);
        
        for (HubDescription d : connectedHubs) { 
            l.add(d.hubAddress);                        
        }
        
        for (HubDescription d : mustCheck) { 
            
            if (d.haveConnection()) {             
                l.add(d.hubAddress);
            } else if (d.state.get() > 0) { 
                l.add(d.hubAddress);
            }
        }
        
        return l.toArray(new DirectSocketAddress[l.size()]);
    }

       
    public String toString() {
        
        StringBuffer result = new StringBuffer();
       
        result.append("Local hub:\n");        
        result.append(localDescription).append("\n");
        
        result.append("Hubs with a direct connection:\n");
        
        for (HubDescription desc : connectedHubs) { 
            result.append(desc).append('\n');            
        }
        
        result.append("Hubs without a direct connection:\n");
        
        /*
        for (HubDescription desc : unConnectedHubs) { 
            result.append(desc).append('\n');            
        }
        */
        
        result.append("Hubs which need to be checked:\n");

        for (HubDescription desc : mustCheck) { 
            result.append(desc).append('\n');            
        }
        
        return result.toString();
    }
}
