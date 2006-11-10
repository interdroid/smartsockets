package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.direct.SocketAddressSet;

public class AddressSelector extends Selector {

    private LinkedList<SocketAddressSet> result = new LinkedList<SocketAddressSet>();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        if (description.getConnection() != null) { 
            result.add(description.hubAddress);
        }
    }

    public LinkedList<SocketAddressSet> getResult() { 
        return result;
    }       
}
