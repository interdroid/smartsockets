package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.direct.DirectSocketAddress;

public class AddressSelector extends Selector {

    private LinkedList<DirectSocketAddress> result = new LinkedList<DirectSocketAddress>();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        if (description.getConnection() != null) { 
            result.add(description.hubAddress);
        }
    }

    public LinkedList<DirectSocketAddress> getResult() { 
        return result;
    }       
}
