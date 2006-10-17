package smartsockets.hub.state;

import java.util.LinkedList;

public class AddressAsStringSelector extends Selector {

    private LinkedList result = new LinkedList();
        
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {       
        result.add(description.hubAddressAsString);
    }
    
    public LinkedList getResult() { 
        return result;
    }   
}
