package smartsockets.hub.state;

import java.util.LinkedList;

public class AddressSelector extends Selector {

    private LinkedList result = new LinkedList();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        if (description.getConnection() != null) { 
            result.add(description.hubAddress);
        }
    }

    public LinkedList getResult() { 
        return result;
    }       
}
