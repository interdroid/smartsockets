package smartsockets.hub.state;

import java.util.LinkedList;

import smartsockets.hub.connections.HubConnection;

public class ConnectionsSelector extends Selector {

    private LinkedList<HubConnection> result = new LinkedList<HubConnection>();
    
    public boolean needAll() {
        return true;
    }
    
    public void select(HubDescription description) {
        
        HubConnection c = description.getConnection(); 
        
        if (c != null) {         
            result.add(c);
        }
    }

    public LinkedList<HubConnection> getResult() { 
        return result;
    }   
}
