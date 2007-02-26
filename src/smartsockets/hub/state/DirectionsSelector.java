package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.direct.DirectSocketAddress;

public class DirectionsSelector extends Selector {
    
    private LinkedList<DirectSocketAddress> good = new LinkedList<DirectSocketAddress>();    
    private LinkedList<DirectSocketAddress> bad = new LinkedList<DirectSocketAddress>();    
    
    private final DirectSocketAddress client;
    private final boolean includeLocal;
    
    public DirectionsSelector(DirectSocketAddress client, boolean includeLocal) { 
        this.client = client;
        this.includeLocal = includeLocal;
    }
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        // Collect the addresses of all proxies that claim to known the client 
        // and are reachable from our location in a single hop. For all proxies 
        // that we can only reach in multiple hops, we return the address of the
        // referring proxy instead (i.e., the 'reachable proxy' that informed us
        // of the existance of the 'unreachable proxy').   
        // 
        // We return the results in list, sorted by how 'good an option' they 
        // are. The order is as follows: 
        // 
        //  1. local proxy (if allowed)
        //
        //  2. proxies that can reach the client directly and that we are 
        //     directly connected to.  
        //
        //  3. indirections for proxies that can reach the client directly, but 
        //     which we cannot reach and (provided that we are directly 
        //     connected to these indirections).   
            
        if (description.containsClient(client)) {

            if (description.isLocal() && includeLocal) {
                good.addFirst(description.hubAddress);                    
            } else if (description.haveConnection()) { 
                good.addLast(description.hubAddress);
            } else {                                         
                HubDescription indirect = description.getIndirection();
                    
                if (indirect != null) { 
                    if (indirect.haveConnection()) { 
                       bad.addFirst(indirect.hubAddress);
                    }
                }
            }
        }
    }

    public LinkedList<DirectSocketAddress> getResult() {

        LinkedList<DirectSocketAddress> result = new LinkedList<DirectSocketAddress>();
        
        result.addAll(good);        
        result.addAll(bad);

        return result;
    }          
}
