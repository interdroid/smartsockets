package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.direct.DirectSocketAddress;

public class DirectionsAsStringSelector extends Selector {
    
    private LinkedList<String> good = new LinkedList<String>();    
    private LinkedList<String> bad = new LinkedList<String>();    
    private LinkedList<String> ugly = new LinkedList<String>();    

    private final DirectSocketAddress client;
    
    public DirectionsAsStringSelector(DirectSocketAddress client) { 
        this.client = client;
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
            
        if (description.containsClient(client)) {

            if (description.isLocal()) {
                good.addFirst(description.hubAddressAsString);                    
            } else if (description.isReachable()) {
                good.addLast(description.hubAddressAsString);
            } else if (description.canReachMe()) { 
                bad.addLast(description.hubAddressAsString);
            } else {                                         
                HubDescription indirect = description.getIndirection();
                    
                if (indirect != null) { 
                    if (indirect.isReachable()) { 
                        ugly.addFirst(indirect.hubAddressAsString);
                    } else if (indirect.canReachMe()) {  
                        ugly.addLast(indirect.hubAddressAsString);
                    }
                }
            }
        }
    }

    public LinkedList<String> getResult() {

        LinkedList<String> result = new LinkedList<String>();
        
        result.addAll(good);        
        result.addAll(bad);
        result.addAll(ugly);

        return result;
    }          
}
