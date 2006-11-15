package smartsockets.hub.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.state.DirectionsSelector;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.HubsForClientSelector;

public abstract class MessageForwardingConnection extends BaseConnection {

    protected final static Logger meslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.messages"); 
    
    protected final static Logger vclogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.virtual"); 
       
    protected final static int DEFAULT_CREDITS = 10; 
    
    protected final VirtualConnections vcs = new VirtualConnections();
    
    private final HashMap<String, String []> connectReplies = 
        new HashMap<String, String []>();
        
    private int nextConnect = 0;
        
    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList proxies) {
        super(s, in, out, connections, proxies);
    }
    
    // returns a unique id.
    protected synchronized String getID() { 
        return "Connect" + nextConnect++;
    }

    // registers that we expect a reply to appear for this id
    protected synchronized void replyPending(String id) {
        connectReplies.put(id, null);        
    }

    // store a reply this id
    protected synchronized boolean storeReply(String id, String [] value) {
        
        if (!connectReplies.containsKey(id)) {
            // reply came too late...
            return false;
        } 
        
        connectReplies.put(id, value);
        notifyAll();
        return true;
    }
    
    // wait for a reply for this id
    protected synchronized String [] waitForReply(String id, int timeout) { 
        
        String [] result = connectReplies.get(id);
        
        long endTime = System.currentTimeMillis() + timeout;
        long timeleft = 0;
        
        while (result == null) {
            
            if (timeout > 0) { 
                timeleft = endTime - System.currentTimeMillis();
            
                if (timeleft <= 0) {
                    break;
                }
            }
            
            try { 
                wait(timeleft);
            } catch (Exception e) {
                // ignore
            }

            result = connectReplies.get(id);
        }
        
        return connectReplies.remove(id);
    }
        
    // Directly sends a message to a hub.
    private boolean directlyToHub(SocketAddressSet hub, ClientMessage cm) {

        BaseConnection c = connections.get(hub); 
        
        if (c != null && c instanceof HubConnection) {             
            HubConnection tmp = (HubConnection) c;            
            tmp.writeMessage(cm);
            return true;
        }   

        return false;
    }
    
    // Tries to forward a message to a given proxy, directly or indirectly.  
    private void forwardMessageToHub(HubDescription p, ClientMessage cm) {
        
        if (meslogger.isDebugEnabled()) { 
            meslogger.debug("Attempting to forward message to hub "
                    + p.hubAddress);
        }
        
        if (directlyToHub(p.hubAddress, cm)) {
            
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Succesfully forwarded message to hub " 
                        + p.hubAddressAsString + " using direct link");
            }
            return;            
        } 
         
        if (cm.hopsLeft == 0) {
            if (meslogger.isInfoEnabled()) {
                meslogger.info("Failed to forward message to hub " 
                        + p.hubAddressAsString + " and we are not allowed to use" 
                        +" an indirection!");
            }
            return;
        } 
            
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Failed to forward message to hub " 
                    + p.hubAddressAsString + " using direct link, " 
                    + "trying indirection");
        }

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
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Succesfully forwarded message to hub " 
                        + p2.hubAddressAsString + " using direct link");
            }
            return;            
        } 

        if (meslogger.isInfoEnabled()) {
            meslogger.info("Failed to forward message to hub "
                    + p.hubAddressAsString + " or it's indirection " 
                    + p2.hubAddressAsString);
        }
    }   
    
    private boolean deliverLocally(ClientMessage cm) {

        // First check if we can find the target locally             
        BaseConnection c = connections.get(cm.target);
        
        if (c == null || !(c instanceof ClientConnection)) {
            meslogger.debug("Cannot find client address locally: " + cm.target);                        
            return false;
        } 

        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Attempting to directly forward message to client " 
                    + cm.target());
        }
           
        // We found the target, so lets forward the message
        boolean result = ((ClientConnection) c).sendMessage(cm);
         
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Directly forwarding message to client " 
                        + cm.target() + (result ? " succeeded!" : "failed!"));
        }
        
        return result;        
    }
    
    private boolean forwardToHub(ClientMessage cm, boolean setHops) {
        
        // Lets see if we directly know the targetHub and if it knows the target         
        if (cm.targetHub == null) {
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Target hub not set!");
            }
            return false;
        }
        
        HubDescription p = knownHubs.get(cm.targetHub);
           
        if (p == null || !p.knowsClient(cm.target)) {
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Target hub " + cm.targetHub 
                        + " does not known " + "client " + cm.target);
            }
            return false;
        }
         
        // The targetHub exists and knows the target, so we're done.
        if (setHops) {         
            cm.hopsLeft = p.getHops();
        }
        
        forwardMessageToHub(p, cm);                
                
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Directly forwarded message to hub: " 
                    + cm.targetHub);                
        }
        
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
                
        HubsForClientSelector hss = new HubsForClientSelector(m.target, false);
        
        knownHubs.select(hss);
        
        for (HubDescription h : hss.getResult()) {
            
            if (setHops) {             
                m.hopsLeft = h.getHops();
            }
            
            forwardMessageToHub(h, m);
        }
    }    
    
    protected abstract String incomingVirtualConnection(
            VirtualConnection origin, MessageForwardingConnection m, 
            SocketAddressSet source, SocketAddressSet target, String info, 
            int timeout);
    
    
    protected abstract String closeVirtualConnection(int index);
    
    protected abstract void forwardVirtualMessage(int index, byte [] data);
    
    protected abstract void forwardVirtualMessageAck(int index);
    
    protected void forwardMessage(int index, byte [] data) {
        
        VirtualConnection vc = vcs.getVC(index);

        if (vc == null) {
            
            vclogger.warn("Got virtual message for non-existing connection!" 
                    + index);
            
            // TODO: send a close back ? 
            return;
        }
        
        if (vc.nextHop == null) { 
            vclogger.warn("Got virtual message for unconnected virtual " +
                    "connection!" + index);            
            // TODO: send a close back ? 
            return;
        }
        
        // TODO: flow control at this point ? 
        vc.nextHop.forwardVirtualMessage(vc.nextVC, data);
    }

    protected void forwardMessageAck(int index) {
        
        VirtualConnection vc = vcs.getVC(index);

        if (vc == null) {
            
            vclogger.warn("Got virtual message ack for non-existing connection!" 
                    + index);
            
            // TODO: send a close back ? 
            return;
        }
        
        if (vc.nextHop == null) { 
            vclogger.warn("Got virtual message ack for unconnected virtual " +
                    "connection!" + index);            
            // TODO: send a close back ? 
            return;
        }
        
        // TODO: flow control at this point ? It's currently end to end...
        vc.nextHop.forwardVirtualMessageAck(vc.nextVC);
    }

    
    protected String forwardAndCloseVirtualConnection(int index) { 
        
        VirtualConnection vc = vcs.getVC(index);

        if (vc == null) {             
            return "Virtual connection " + index + " not found!";
        }
        
        if (vc.nextHop == null) {
            vcs.freeVC(vc);            
            return "Virtual connection " + index + " not connected!";
        }
        
        return vc.nextHop.closeVirtualConnection(vc.nextVC);
    }
    
    protected String createVirtualConnection(int index, SocketAddressSet source,
            SocketAddressSet target, String info, int timeout) {
        
        VirtualConnection vc = vcs.newVC(index);
                        
        System.err.println("EEEEEKEKEKEKEE ---- " + vc);
        
        // Check if the client is connected to the local hub...
        BaseConnection tmp = connections.get(target);
        
        if (tmp != null) {
            
            if (!(tmp instanceof ClientConnection)) { 
                // apparently, the user is trying to connect to a hub here, 
                // which is not allowed!                 
                return "Connection to hub not allowed";
            }
            
            if (tmp == this) {
                // connecting to oneself over a hub is generally not a good idea 
                // although it should work ? 
                return "Connection to self not allowed";                 
            }
            
            ClientConnection cc = (ClientConnection) tmp;   
            
            return cc.incomingVirtualConnection(vc, this, source, target, info,
                    timeout);
        } 
        
        // Find the hubs that know the client...  
        DirectionsSelector ds = new DirectionsSelector(target, false);
        
        knownHubs.select(ds);
        
        LinkedList<SocketAddressSet> result = ds.getResult();
        
        if (result.size() == 0) {
            return "Unknown host";
        }             
        
        for (SocketAddressSet s : result) { 
            
            HubConnection h = (HubConnection) connections.get(s);

            if (h != null) { 
                String r = h.incomingVirtualConnection(vc, this, source, target,
                        info, timeout);

                if (r == null) {
                    // apparently, we have a connection 
                    return null;
                }
            }
        }
        
        // TODO: see if any of the attempts returned a better error ? 
        return "No route to host";
    }
    
}
