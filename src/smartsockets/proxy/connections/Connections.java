package smartsockets.proxy.connections;

import java.util.HashMap;

public class Connections {
   
    private HashMap connections; 
    
    public Connections()  { 
        connections = new HashMap();
    }
    
    public synchronized void addConnection(String key, BaseConnection c) { 
        connections.put(key, c);
    }

    public synchronized BaseConnection getConnection(String key) { 
        return (BaseConnection) connections.get(key);
    }

    public synchronized void removeConnection(String key) { 
        connections.remove(key);
    }
}
