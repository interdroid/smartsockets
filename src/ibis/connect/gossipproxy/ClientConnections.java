package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.util.ForwarderDoneCallback;

import java.util.HashMap;

import org.apache.log4j.Logger;

class ClientConnections implements ForwarderDoneCallback {
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ClientConnections.class.getName());
    
    private HashMap connections = new HashMap();
        
    public synchronized void addConnection(String id, Connection c) {         
        connections.put(id, c);                
    }
        
    public synchronized void done(Object id) {
        
        Connection c = (Connection) connections.get(id);            

        if (c == null) {
            logger.error("Received callback for unknown connection " + id);
            return;
        }

        logger.info("Received callback for connection " + id);
        
        // Check if both connections are done so we can close the connection...
        if (c.forwarder1.isDone() && c.forwarder1.isDone()) { 
            logger.info("Removing connection " + id + " since it is done!");
            connections.remove(id);
            
            DirectSocketFactory.close(c.socketA, c.outA, c.inA);
            DirectSocketFactory.close(c.socketB, c.outB, c.inB);
        } else { 
            logger.info("Cannot remove connection " + id + " yet!");
        }
    }      
}
