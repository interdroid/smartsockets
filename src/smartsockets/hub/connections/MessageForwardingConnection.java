package smartsockets.hub.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.UnknownHostException;
import java.util.Iterator;
import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;

public abstract class MessageForwardingConnection extends BaseConnection {

    protected static Logger meslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.messages"); 
        
    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, HubList proxies) {
        super(s, in, out, connections, proxies);
    }
    
    // Directly sends a message to a hub.
    private boolean directlyToHub(SocketAddressSet hub, ClientMessage cm) {

        BaseConnection c = connections.getConnection(hub); 
        
        if (c != null && c instanceof HubConnection) {             
            HubConnection tmp = (HubConnection) c;            
            tmp.writeMessage(cm);
            return true;
        }   

        return false;
    }
    
    // Tries to forward a message to a given proxy, directly or indirectly.  
    private void forwardMessageToHub(HubDescription p, ClientMessage cm) {
        
        meslogger.debug("Attempting to forward message to hub "
                + p.hubAddress);
        
        if (directlyToHub(p.hubAddress, cm)) {
            
            meslogger.debug("Succesfully forwarded message to hub " 
                    + p.hubAddressAsString + " using direct link");
            return;            
        } 
         
        if (cm.hopsLeft == 0) {
            meslogger.info("Failed to forward message to hub " 
                    + p.hubAddressAsString + " and we are not allowed to use" 
                    +" an indirection!");
            return;
        } 
            
        meslogger.debug("Failed to forward message to hub " 
                + p.hubAddressAsString + " using direct link, " 
                + "trying indirection");

        // We don't have a direct connection, but we should be able to reach the
        // proxy indirectly
        HubDescription p2 = p.getIndirection();
        
        if (p2 == null) {
            // Oh dear, we don't have an indirection!
            meslogger.warn("Indirection address of " + p.hubAddressAsString
                    + " is null!");
            return;
        } 

        if (directlyToHub(p2.hubAddress, cm)) { 

            meslogger.debug("Succesfully forwarded message to hub " 
                    + p2.hubAddressAsString + " using direct link");
            return;            
        } 

        meslogger.info("Failed to forward message to hub " + p.hubAddressAsString 
                + " or it's indirection " + p2.hubAddressAsString);
    }   
    
    private boolean deliverLocally(ClientMessage cm) {

        // First check if we can find the target locally             
        BaseConnection c = (BaseConnection) connections.getConnection(cm.target);
        
        if (c == null || !(c instanceof ClientConnection)) {
            meslogger.debug("Cannot find client address locally: " + cm.target);                        
            return false;
        } 

        meslogger.debug("Attempting to directly forward message to client " 
                + cm.target());
           
        // We found the target, so lets forward the message
        boolean result = ((ClientConnection) c).sendMessage(cm);
            
        meslogger.debug("Directly forwarding message to client " 
                    + cm.target() + (result ? " succeeded!" : "failed!"));                
        
        return result;        
    }
    
    private boolean forwardToHub(ClientMessage cm, boolean setHops) {
        
        // Lets see if we directly know the targetHub and if it knows the target         
        if (cm.targetHub == null) {
            meslogger.debug("Target hub not set!");                        
            return false;
        }
        
        HubDescription p = knownHubs.get(cm.targetHub);
           
        if (p == null || !p.knowsClient(cm.target)) {
            meslogger.debug("Target hub " + cm.targetHub + " does not known " +
                    "client " + cm.target);                        
            return false;
        }
         
        // The targetHub exists and knows the target, so we're done.
        if (setHops) {         
            cm.hopsLeft = p.getHops();
        }
        
        forwardMessageToHub(p, cm);                
                
        meslogger.debug("Directly forwarded message to hub: " + cm.targetHub);                

        return true;
    }
    
    
    protected void forward(ClientMessage m, boolean setHops) {     
     
        // Try to deliver the message directly to the client.
        if (deliverLocally(m)) { 
            return;
        }
        
        // Try to forward the message to the right hub.   
        if (forwardToHub(m, setHops)) { 
            return;
        }
                
        // See if we can find any other hubs that know this target
        Iterator itt = knownHubs.findHubsForTarget(m.target, false);
        
        while (itt.hasNext()) { 
            HubDescription h = (HubDescription) itt.next();
            
            if (setHops) {             
                m.hopsLeft = h.getHops();
            }
            
            forwardMessageToHub(h, m);
        }
    }    
}
