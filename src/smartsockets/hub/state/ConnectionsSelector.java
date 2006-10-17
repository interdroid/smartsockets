package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.hub.connections.HubConnection;

public class ConnectionsSelector extends Selector {

    private LinkedList result = new LinkedList();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        HubConnection c = description.getConnection(); 
        
        if (c != null) {         
            result.add(c);
        }
    }

    public LinkedList getResult() { 
        return result;
    }   
}
