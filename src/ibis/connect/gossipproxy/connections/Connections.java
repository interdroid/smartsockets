package ibis.connect.gossipproxy.connections;

import java.util.HashMap;

import org.apache.log4j.Logger;

public class Connections {

    private static Logger logger = 
        ibis.util.GetLogger.getLogger(Connections.class.getName());
    
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
