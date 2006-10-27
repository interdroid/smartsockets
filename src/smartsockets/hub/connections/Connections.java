package smartsockets.hub.connections;

import java.util.HashMap;

import smartsockets.direct.SocketAddressSet;

public class Connections {
   
    private HashMap connections; 
    
    public Connections()  { 
        connections = new HashMap();
    }
    
    public synchronized void addConnection(SocketAddressSet key, BaseConnection c) { 
        connections.put(key, c);
    }

    public synchronized BaseConnection getConnection(SocketAddressSet key) { 
        return (BaseConnection) connections.get(key);
    }

    public synchronized void removeConnection(SocketAddressSet key) { 
        connections.remove(key);
    }
}
