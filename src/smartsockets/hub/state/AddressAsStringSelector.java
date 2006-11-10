package smartsockets.hub.state;

import java.util.LinkedList;

public class AddressAsStringSelector extends Selector {

    private LinkedList<String> result = new LinkedList<String>();
        
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {       
        result.add(description.hubAddressAsString);
    }
    
    public LinkedList<String> getResult() { 
        return result;
    }   
}
