package smartsockets.hub.state;

import java.util.LinkedList;

public class ClientsByTagAsStringSelector extends Selector {
    
    private LinkedList<String> result = new LinkedList<String>();
    
    private final String tag;
    
    public ClientsByTagAsStringSelector(String tag) { 
        this.tag = tag;
    }
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        description.getClientsAsString(result, tag);
    }
    
    public LinkedList<String> getResult() {
        return result;
    }          
}
