package ibis.smartsockets.hub.state;

import ibis.smartsockets.hub.connections.HubConnection;

import java.util.LinkedList;


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
