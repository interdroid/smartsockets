package smartsockets.hub.connections;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.sun.java_cup.internal.runtime.Symbol;

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
    
    protected final VirtualConnections virtualConnections;
    protected final VirtualConnectionIndex index;
    
    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList proxies, VirtualConnections vcs, boolean master) {
       
        super(s, in, out, connections, proxies);
        
        this.virtualConnections = vcs;
        index = new VirtualConnectionIndex(master);
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
    
    //// Virtual connection parts...
    protected abstract String getUniqueID(long index);
    
    protected abstract void forwardVirtualConnect(SocketAddressSet source, 
            SocketAddressSet target, SocketAddressSet targetHub, String info, 
            int timeout, long index);
    
    protected abstract void forwardVirtualConnectAck(long index, String result, 
            String info);
    
    protected abstract void forwardVirtualClose(long index);
    
    protected abstract void forwardVirtualMessage(long index, byte [] data);
    
    protected abstract void forwardVirtualMessageAck(long index);
    
    protected void processMessage(long index, byte [] data) { 
        
        String key = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout. Send a close back to inform the sender 
            // that the connection does no longer exist...
            forwardVirtualClose(index);
            return; 
        }
  
        // We found the connection so we have to figure out which of the two 
        // entries in the VC is ours. The easiest way is to simply compare the 
        // 'mfX' references to 'this'. Note that we cannot compare the index 
        // values, since they are not unique!
        if (this == vc.mfc1) { 
        
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward message " + index + " for 2: "
                        + vc.index2 + "(" + vc.index1 + ")");
            }
            
            vc.mfc2.forwardVirtualMessage(vc.index2, data);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward message " + index + " for 1: " 
                        + vc.index1 + " (" + vc.index2 + ")");
            }
            
            vc.mfc1.forwardVirtualMessage(vc.index1, data);
        
        } else { 
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }              
    }
    
    protected void processMessageACK(long index) { 
        
        String key = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout. Send a close back to inform the sender 
            // that the connection does no longer exist...
            forwardVirtualClose(index);
            return; 
        }
  
        // We found the connection so we have to figure out which of the two 
        // entries in the VC is ours. The easiest way is to simply compare the 
        // 'mfX' references to 'this'. Note that we cannot compare the index 
        // values, since they are not unique!
        if (this == vc.mfc1) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 2: " + vc.index2);
            }
            
            vc.mfc2.forwardVirtualMessageAck(vc.index2);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualMessageAck(vc.index1);
        
        } else { 
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }              
    }
    
    protected void closeVirtualConnection(long index) { 

        String id = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.remove(id);
        
        if (vc == null) {
            // Connection doesn't exist. It may already be closed (this can 
            // happen if the other side beat us to closing it).    
            if (vclogger.isInfoEnabled()) {
                vclogger.info("Virtual connection " + index + " not found!");
            } 
            return;
        }  
          
        if (vclogger.isInfoEnabled()) {                                    
            vclogger.info("Close virtual connection: " + vc);
        }
        
        // We now have to figure out which of the two entries in the VC is ours.
        // The easiest way is to simply compare the 'mfX' references.
        if (this == vc.mfc1) { 
        
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward close for 2: " + vc.index2);
            }
            
            vc.mfc2.forwardVirtualClose(vc.index2);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward close for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualClose(vc.index1);
        
        } else { 
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }              
    }
        
    private VirtualConnection createConnection(MessageForwardingConnection mfc1,
            String id1, long index1) { 
        
        long index2 = index.nextIndex();
        String id2 = getUniqueID(index2);
        
        return new VirtualConnection(mfc1, id1, index1, this, id2, index2);
    }
         
    protected void processVirtualConnect(long index, SocketAddressSet source, 
            SocketAddressSet target, SocketAddressSet targetHub, String info, 
            int timeout) {
        
        if (vclogger.isInfoEnabled()) {                            
            vclogger.info("ANY connection request for: " + index + " to " + 
                    target);
        }
        
        MessageForwardingConnection mf = null;
        
        // Check if the client is connected to the local hub...
        BaseConnection tmp = connections.get(target);
        
       // vclogger.warn("Local lookup of " + target + " result = " + tmp);
        
        if (tmp != null) {
            
            if (!(tmp instanceof ClientConnection)) { 
                // apparently, the user is trying to connect to a hub here, 
                // which is not allowed! Send a NACK back!
                forwardVirtualConnectAck(index, "DENIED", 
                        "Connection to hub not allowed");
                
                return;
            }
            
            if (tmp == this) {
                // connecting to oneself over a hub is generally not a good idea 
                // although it should work ?
                forwardVirtualConnectAck(index, "DENIED", 
                        "Connection to self not allowed");
                
                return;
            }
        
            mf = (MessageForwardingConnection) tmp;
           
        } else if (targetHub != null) { 
            
         //   vclogger.warn("trying to connect via hub: " + targetHub);
            
            mf = (MessageForwardingConnection) connections.get(targetHub);        
            
            if (mf == null) {
                
             //   vclogger.warn("failed, trying to connect via indirection");
                
                // Failed to get a connection to the specified hub. Maybe there 
                // is an indirection ? 
                HubDescription d = knownHubs.get(targetHub);
                           
                if (d != null) {  
                    HubDescription indirect = d.getIndirection();
                    
                    if (indirect != null && indirect.haveConnection()) { 
                        mf = (MessageForwardingConnection) 
                            connections.get(indirect.hubAddress);
                        
                       // vclogger.warn("GOT indirection!");
                        
                    } else { 
                        vclogger.warn("Failed to find indirection for hub: " 
                                + targetHub);
                    }
                } else { 
                    vclogger.warn("Failed to find hub: " + targetHub);
                }
            }
        } 
        
        if (mf == null) { 
            DirectionsSelector ds = new DirectionsSelector(target, false);

            knownHubs.select(ds);

            LinkedList<SocketAddressSet> result = ds.getResult();

            if (result.size() > 0) {
                // TODO: send in Multiple directions.... ?
                mf = (MessageForwardingConnection) connections.get(result.get(0));
            }
            
            // NOTE: we may not be able to find the client here, since we don't 
            // know is exist yet (the gossip hasn't reached us). This case is 
            // handled in the client-side code, by retrying the connect until 
            // the timeout expires.. 
        } 
        
        if (mf == null) {
            // Connection setup failed!
            forwardVirtualConnectAck(index, "DENIED", "Unknown host");
            return;
        }             
        
        // We found a target connection, so let's create the necessary 
        // connection administration....
            
        // Get a unique id so that we can find the connection again later...
        String id = getUniqueID(index);

        // We now delegate the actual creation of the connection object to 
        // the target (since it has the rest of the required info).
        VirtualConnection vc = mf.createConnection(this, id, index);

        // Register the virtual connection, so everyone can find it 
        virtualConnections.register(vc);

        // Ask the target to forward the connect message to whoever it 
        // represents (a client or a hub). This should be an asynchronous 
        // call to prevent deadlocks!!
        mf.forwardVirtualConnect(source, target, targetHub, info, timeout, vc.index2);
    }
   
    protected void processVirtualConnectACK(long index, String result, 
            String info) { 
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Got create connect ACK: " + index + " " + result 
                    + " " + info);
        }
        
        String key = getUniqueID(index);
        
        VirtualConnection vc = null; 
       
        // Check if we got an ACK or a NACK
        if (result.equals("OK")) {
            // It's an ACK, so we just retrieve the connection...
            vc = virtualConnections.find(key);
        } else {
            // It's a NACK so we remove the connection, since it's no 
            // longer used after we forwarded the reply
            vc = virtualConnections.remove(key);
        }
        
       // vclogger.warn("Got vc: " + vc);
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout. 
            
            if (result.equals("OK")) { 
                // if the reply is an ACK, we send a close back to inform the 
                // sender that the connection does no longer exist...
                forwardVirtualClose(index);
            } 
            
            // else it's a NACK and we're done!   
            return; 
        }
        
        // We found the connection so we have to figure out which of the two 
        // entries in the VC is ours. The easiest way is to simply compare the 
        // 'mfX' references to 'this'. Note that we cannot compare the index 
        // values, since they are not unique!
        if (this == vc.mfc1) { 
        
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 2: " + vc.index2);
            }
            
            vc.mfc2.forwardVirtualConnectAck(vc.index2, result, info);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualConnectAck(vc.index1, result, info);
        
        } else { 
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }    
    }
    
    // Called when a connection to a client/hub is lost....
    protected void closeAllVirtualConnections(String prefix) { 
     
        LinkedList<VirtualConnection> l = virtualConnections.removeAll(prefix);
        
        for (VirtualConnection vc : l) { 
            
            // We now have to figure out which of the two entries in the VC 
            // is ours. The easiest way is to simply compare the 'mfX' 
            // references.
            if (this == vc.mfc1) { 
            
                if (vclogger.isInfoEnabled()) {                                    
                    vclogger.info("forward close for 2: " + vc.index2);
                }
                
                vc.mfc2.forwardVirtualClose(vc.index2);
                
            } else if (this == vc.mfc2) { 
                
                if (vclogger.isInfoEnabled()) {                                    
                    vclogger.info("forward close for 1: " + vc.index1);
                }
                
                vc.mfc1.forwardVirtualClose(vc.index1);
            
            } else { 
                // This should never happen!        
                vclogger.warn("Virtual connection error: forwarder not found!", 
                        new Exception());
            }              
        }
    }    
}
