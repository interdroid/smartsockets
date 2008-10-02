package ibis.smartsockets.hub.connections;

import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.ConnectionProtocol;
import ibis.smartsockets.hub.Connections;
import ibis.smartsockets.hub.servicelink.ServiceLinkProtocol;
import ibis.smartsockets.hub.state.DirectionsSelector;
import ibis.smartsockets.hub.state.HubDescription;
import ibis.smartsockets.hub.state.HubList;
import ibis.smartsockets.hub.state.HubsForClientSelector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class MessageForwardingConnection extends BaseConnection {

    protected final static Logger meslogger = 
        LoggerFactory.getLogger("ibis.smartsockets.hub.messages"); 
    
    protected final static Logger vclogger = 
        LoggerFactory.getLogger("ibis.smartsockets.hub.connections.virtual"); 
       
    protected final static int DEFAULT_CREDITS = 10; 
    
    protected final VirtualConnections virtualConnections;
    protected final VirtualConnectionIndex index;
    
    private long connectionsTotal;
    private long connectionsFailed;
    
    private long connectionsReplies;
    private long connectionsACKs;
    private long connectionsNACKs;
    private long connectionsRepliesLost;
    private long connectionsRepliesError;
        
    private long closeTotal;
    private long closeError;
    private long closeLost;
    
    private long messages;
    private long messagesError;
    private long messagesLost; 
    private long messagesBytes;
   
    private long messageACK;
    private long messageACK_Error;
    private long messageACKLost; 
    
    private final String name; 
    
    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, HubList hubs, 
            VirtualConnections vcs, boolean master, String name) {
       
        super(s, in, out, connections, hubs);
        
        this.name = name;
        
        this.virtualConnections = vcs;
        index = new VirtualConnectionIndex(master);
    }

    // Directly sends a message to a hub.
    private boolean directlyToHub(DirectSocketAddress hub, ClientMessage cm) {

        HubConnection c = connections.getHub(hub); 
        
        if (c != null) {             
            return c.forwardClientMessage(cm);
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
        ClientConnection c = connections.getClient(cm.getTarget());
        
        if (c == null) {
            meslogger.debug("Cannot find client address locally: " + cm.getTarget());                        
            return false;
        } 

        if (meslogger.isDebugEnabled()) {
            
            if (cm.returnToSender) { 
                meslogger.debug("Attempting to directly return message to sender " 
                        + cm.sourceAsString());                
            } else {             
                meslogger.debug("Attempting to directly forward message to client " 
                        + cm.targetAsString());
            }
        }
           
        // We found the target, so lets forward the message
        boolean result = c.forwardClientMessage(cm);
         
        if (meslogger.isDebugEnabled()) {
            if (cm.returnToSender) {
                meslogger.debug("Directly return message to sender " 
                        + cm.sourceAsString() + (result ? " succeeded!" : "failed!"));
            } else { 
                meslogger.debug("Directly forwarding message to client " 
                        + cm.targetAsString() + (result ? " succeeded!" : "failed!"));                
            }
        }
        
        return result;        
    }
    
    private boolean forwardToHub(ClientMessage cm, boolean setHops) {
        
        DirectSocketAddress hub = cm.getTargetHub();
        
        // Lets see if we directly know the targetHub and if it knows the target         
        if (hub == null) {
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Target hub not set!");
            }
            return false;
        }
        
        HubDescription p = knownHubs.get(cm.getTarget());
           
        if (p == null) {
            if (meslogger.isDebugEnabled()) {
                meslogger.debug("Target hub " + hub + " does not know client " 
                        + cm.getTarget());
            }
            return false;
        }
         
        // The targetHub exists so forward the message.
        if (setHops) {         
            cm.hopsLeft = p.getHops();
        }
        
        forwardMessageToHub(p, cm);                
                
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Directly forwarded message to hub: " + hub);                
        }
        
        return true;
    }
    
    
    protected void forward(ClientMessage m, boolean setHops) {   
        
        if (m.getSourceHub() == null) { 
            // should never happen, but we like to programm defensively
            m.setSourceHub(getLocalHub());
        }
        
        DirectSocketAddress hub = m.getTargetHub();
        
        if (hub == null) { 
       
            // Try to deliver the message to a local client.
            if (deliverLocally(m)) { 
                return;
            }
            
            // If this fails, we try to find the right hub and forward the 
            // message... 
            //
            // TODO: reimplement this with a bcast to all hubs instead of  
            //       relying on client info to be gossiped in advance ?         
            HubsForClientSelector hss = new HubsForClientSelector(
                    m.getTarget(), false);
            
            knownHubs.select(hss);
            
            LinkedList<HubDescription> result = hss.getResult();
            
            if (result.size() == 0) {
                // No hubs were found that known the client, so lets return the 
                // message to the sender...
                if (m.returnToSender) {
                    if (meslogger.isDebugEnabled()) {
                        meslogger.debug("Cannot return message to sender, since it " 
                                + " has disappeared! (source= " + m.targetAsString() 
                                + " target=" + m.sourceAsString());                
                    }
                } else { 
                    if (meslogger.isDebugEnabled()) {
                        meslogger.debug("Return message to sender, since target is " 
                                + " not known! (source= " + m.sourceAsString()
                                + " target=" + m.targetAsString());                
                    }
                    returnToSender(m);
                }
                return;
            }
                        
            for (HubDescription h : result) {
                
                if (setHops) {             
                    m.hopsLeft = h.getHops();
                }
                
                forwardMessageToHub(h, m);
            }    
            
        } else {
             
            if (isLocalHub(hub)) { 

                if (deliverLocally(m)) { 
                    return;
                } else { 
                    returnToSender(m);
                }
                
            } else {
                
                if (forwardToHub(m, setHops)) { 
                    return;
                } else { 
                    returnToSender(m);    
                }
            }
        } 
    }    
    
    private void returnToSender(ClientMessage m) {
        
      //  System.out.println("**** Returning to sender....");
        
        if (m.returnToSender) {
            // should never happen!
     //       System.out.println("**** Returning to sender says EEK");
            return;
        }
        
   //     System.out.println("**** Returning to sender says her I go!");
      
        m.returnToSender = true;
        forward(m, true);
    }

    protected final boolean forwardClientMessage(ClientMessage m) { 
        
        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.INFO_MESSAGE);            
                m.write(out);            
                out.flush();
            }
            return true;
        } catch (Exception e) {
            handleDisconnect(e);
            
            if (meslogger.isDebugEnabled()) { 
                meslogger.debug("Forwarding message failed: " + m, e);                
            } else { 
                // TODO: remove
                meslogger.warn("Forwarding message failed: " + m, e);
            }
            
            return false;
        }                
    }
    
    // Virtual connection parts...
    protected final void handleCreateVirtual() throws IOException { 
        
        DirectSocketAddress source = DirectSocketAddress.read(in);
        DirectSocketAddress sourceHub = DirectSocketAddress.read(in);
        
        DirectSocketAddress target = DirectSocketAddress.read(in);
        DirectSocketAddress targetHub = DirectSocketAddress.read(in);

        long index = in.readLong();
        
        int timeout = in.readInt();        
        int port = in.readInt();
        int fragment = in.readInt();
        int buffer = in.readInt();
        
        if (vclogger.isInfoEnabled()) {                                    
            vclogger.info("VC connection request for: " + index);
        }
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Creating virtual connection " 
                    + source + " -->> " + target);
        }
        
        processVirtualConnect(source, sourceHub, target, targetHub, index, 
                timeout, port, fragment, buffer);
    }
    
    protected final void handleCloseVirtual() throws IOException { 
        
        long index = in.readLong();     
        
        if (vclogger.isInfoEnabled()) {                        
            vclogger.info("Locally closing VC: " + index);
        }
        
        closeVirtualConnection(index);  
    } 
    
    private final void skipBytes(int bytes) throws IOException { 
       
        while (bytes > 0) { 
            bytes -= in.skipBytes(bytes);
        }
    }
    
    protected final void handleMessageVirtual() throws IOException { 
        
        long index = in.readLong();
        int size = in.readInt();
        
        messages++;
        
        messagesBytes += size;
            
        String key = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout. Send a close back to inform the sender 
            // that the connection does no longer exist...
            vclogger.info("Lost message! " + index + "[" + size 
                    + "] target VC not found!");
        
            messagesLost++;
            
            skipBytes(size);
            
            forwardVirtualClose(index);
            return; 
        }
  
        // We found the connection so we have to figure out which of the two 
        // entries in the VC is ours. The easiest way is to simply compare the 
        // 'mfX' references to 'this'. Note that we cannot compare the index 
        // values, since they are not unique!
        if (this == vc.mfc1) { 
        
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward message " + index + " "
                        + vc.index2 + " (" + vc.index1 + ")");
            }

            // Read the data into the (pre allocated) buffer.
            in.readFully(vc.buffer1, 0, size);
            vc.mfc2.forwardVirtualMessage(vc.index2, vc.buffer1, size);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward message " + index + " " 
                        + vc.index1 + " (" + vc.index2 + ")");
            }

            // Read the data into the (pre allocated) buffer.
            in.readFully(vc.buffer2, 0, size);            
            vc.mfc1.forwardVirtualMessage(vc.index1, vc.buffer2, size);
        
        } else {
            messagesError++;
           
            skipBytes(size);
            
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found, " +
                    "message lost!!!", new Exception());
        }              
    }
    
    protected final void handleMessageVirtualAck() throws IOException {
        
        long index = in.readLong();        
        int data   = in.readInt();        
        
        processMessageACK(index, data);
    }
        
    protected final void handleACKCreateVirtualConnection() throws IOException { 
        
        long index   = in.readLong();        
        int fragment = in.readInt();        
        int buffer   = in.readInt();
        
        processVirtualConnectACK(index, fragment, buffer);
    } 
    
    protected final void handleACKACKCreateVirtualConnection() throws IOException { 
        
        long index     = in.readLong();      
        boolean succes = in.readBoolean();
        
       // System.err.println("****** ACK ACK IN " + index + " " + succes);
        
        processVirtualConnectACKACK(index, succes);
    } 
    
    protected final void handleNACKCreateVirtualConnection() throws IOException { 
        
        long index   = in.readLong();        
        byte reason  = in.readByte();
        
        processVirtualConnectNACK(index, reason);
    } 
    
    protected final void handleClientMessage() throws IOException {        
        ClientMessage cm = new ClientMessage(in);        
        
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Got message: " + cm);
        }
        
        forward(cm, false);          
    }
    
    protected abstract void handleDisconnect(Exception e); 
    
    protected abstract String getUniqueID(long index);
        
    private final void forwardVirtualConnect(DirectSocketAddress source,
            DirectSocketAddress sourceHub, DirectSocketAddress target, 
            DirectSocketAddress targetHub, long index, int timeout, int port, 
            int fragment, int buffer) { 
        
        // TODO: Should be asynchronous ???
        
        // Send the connect request to the hub
        try { 
            synchronized (out) {
                out.write(MessageForwarderProtocol.CREATE_VIRTUAL);           
                
                DirectSocketAddress.write(source, out);
                DirectSocketAddress.write(sourceHub, out);
                
                DirectSocketAddress.write(target, out);
                DirectSocketAddress.write(targetHub, out);
        
                out.writeLong(index);
                
                out.writeInt(timeout);                
                out.writeInt(port);
                out.writeInt(fragment);                
                out.writeInt(buffer);
                
                out.flush();
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }
    }
    
    private final void forwardVirtualConnectACK(long index, int fragment, 
            int buffer) { 
        
        // TODO: Should be asynchronous ???
        
        // forward the ACK
        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL_ACK);           
                out.writeLong(index);
                out.writeInt(fragment);
                out.writeInt(buffer);                
                out.flush();
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }        
    }
    
    private final void forwardVirtualConnectACKACK(long index, boolean succes) { 
        
        // TODO: Should be asynchronous ???
        
       // System.err.println("****** ACK ACK OUT " + index + " " + succes);
        
        // forward the ACK
        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL_ACK_ACK);           
                out.writeLong(index);
                out.writeBoolean(succes);
                out.flush();
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }        
        
      //  System.err.println("****** ACK ACK OUT DONE " + index + " " + succes);
    }
    
    private final void forwardVirtualConnectNACK(long index, byte reason) { 
        // TODO: Should be asynchronous ???
        
        // forward the NACK
        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL_NACK);           
                out.writeLong(index);
                out.writeByte(reason);
                out.flush();
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }        
    }
    
    private final void forwardVirtualClose(long index) { 

        // TODO: Should be asynchronous ???
        
        if (vclogger.isInfoEnabled()) {                                   
            vclogger.info("Sending closing connection: " + index);
        }    
        // forward the close
        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.CLOSE_VIRTUAL);           
                out.writeLong(index);            
                out.flush();
            }            
        } catch (Exception e) {
            handleDisconnect(e);
        }
    }
    
    private final void forwardVirtualMessage(long index, byte [] data, int size) {
        
        // TODO: Should be asynchronous ???
        
        // forward the message
        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.MESSAGE_VIRTUAL);           
                out.writeLong(index);
                out.writeInt(size);
                out.write(data, 0, size);
                out.flush();                
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }        
    }

    private final void forwardVirtualMessageAck(long index, int data) { 

        // TODO: Should be asynchronous ???
        
        // forward the message ack
        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.MESSAGE_VIRTUAL_ACK);           
                out.writeLong(index);
                out.writeInt(data);
                out.flush();                
            }
        } catch (Exception e) {
            handleDisconnect(e);
        }        
    }
    
    private void processMessageACK(long index, int data) { 
        
        messageACK++;
        
        String key = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            
            messageACKLost++;
            
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
            
            vc.mfc2.forwardVirtualMessageAck(vc.index2, data);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualMessageAck(vc.index1, data);
        
        } else { 
            
            messageACK_Error++;
            
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }              
    }
    
    private void closeVirtualConnection(long index) { 

        closeTotal++;
        
        String id = getUniqueID(index);
        
        VirtualConnection vc = virtualConnections.remove(id);
        
        if (vc == null) {
            // Connection doesn't exist. It may already be closed (this can 
            // happen if the other side beat us to closing it).    
           
            closeLost++;
            
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
            closeError++;
            
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }              
    }
        
    private VirtualConnection createConnection(MessageForwardingConnection mfc1,
            String id1, long index1, int fragment1) { 
        
        long index2 = index.nextIndex();
        String id2 = getUniqueID(index2);
        
        return new VirtualConnection(mfc1, id1, index1, fragment1, this, id2, 
                index2);
    }
         
    private void processVirtualConnect(DirectSocketAddress source, 
            DirectSocketAddress sourceHub, DirectSocketAddress target, 
            DirectSocketAddress targetHub, long index, int timeout, int port, 
            int fragment, int buffer) {
        
        connectionsTotal++;
        
        if (vclogger.isInfoEnabled()) {                            
            vclogger.info("ANY connection request for: " + index + " to " + 
                    target);
        }
        
        MessageForwardingConnection mf = null;
        
        // Check if the client is connected to the local hub...
        ClientConnection tmp = connections.getClient(target);
        
        if (tmp != null) { 
            
            if (tmp == this) {
                // connecting to oneself over a hub is generally not a good idea 
                // although it should work ?
                forwardVirtualConnectNACK(index, 
                        ServiceLinkProtocol.ERROR_ILLEGAL_TARGET);
                
                connectionsFailed++;                
                return;
            }
        
            mf = (MessageForwardingConnection) tmp;           
        } 
        
        if (mf == null && targetHub != null) { 
            
            mf = connections.getHub(targetHub);        
            
            if (mf == null) {
                // Failed to get a connection to the specified hub. Maybe there 
                // is an indirection ? 
                HubDescription d = knownHubs.get(targetHub);
                           
                if (d != null) {  
                    HubDescription indirect = d.getIndirection();
                    
                    if (indirect != null && indirect.haveConnection()) { 
                        mf = connections.getHub(indirect.hubAddress);
                    } else { 
                        vclogger.info("Failed to find indirection for hub: " 
                                + targetHub 
                                + " during virtual connection setup ("
                                + source + " -> " + target + ") : "  + index);
                    }
                } else { 
                    vclogger.info("Failed to find hub: " + targetHub 
                            + " during virtual connection setup ("
                            + source + " -> " + target + ") : "  + index); 
                }
            }
        } 
        
        if (mf == null) { 
            DirectionsSelector ds = new DirectionsSelector(target, false);

            knownHubs.select(ds);

            LinkedList<DirectSocketAddress> result = ds.getResult();

            if (result.size() > 0) {
                // TODO: send in Multiple directions.... ?
                mf = connections.getHub(result.get(0));
            }
            
            // NOTE: we may not be able to find the client here, since we don't 
            // know is exist yet (the gossip hasn't reached us). This case is 
            // handled in the client-side code, by retrying the connect until 
            // the timeout expires.. 
        } 
        
        if (mf == null) {
            // Connection setup failed!
            forwardVirtualConnectNACK(index, 
                    ServiceLinkProtocol.ERROR_UNKNOWN_HOST);

            connectionsFailed++;
            return;
        }             
        
        // We found a target connection, so let's create the necessary 
        // connection administration....
            
        // Get a unique id so that we can find the connection again later...
        String id = getUniqueID(index);

        // We now delegate the actual creation of the connection object to 
        // the target (since it has the rest of the required info).
        VirtualConnection vc = mf.createConnection(this, id, index, fragment);

        // Register the virtual connection, so everyone can find it 
        virtualConnections.register(vc);

        // Ask the target to forward the connect message to whoever it 
        // represents (a client or a hub). This should be an asynchronous 
        // call to prevent deadlocks!!
        mf.forwardVirtualConnect(source, sourceHub, target, targetHub, 
                vc.index2, timeout, port, fragment, buffer);
    }
   
    private void processVirtualConnectNACK(long index, byte reason) { 

        connectionsReplies++;
        connectionsNACKs++;
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Got connect NACK: " + index + " " + reason);  
        }
        
        // It's a NACK so we remove the connection, since it's no 
        // longer used after we forwarded the reply
        VirtualConnection vc = virtualConnections.remove(getUniqueID(index));
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout.             
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
            
            vc.mfc2.forwardVirtualConnectNACK(vc.index2, reason);
            
        } else if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualConnectNACK(vc.index1, reason);
        
        } else { 
            
            connectionsRepliesError++;
            
            // This should never happen!        
            vclogger.error("Virtual connection error: forwarder not found!", 
                    new Exception());
        }
    }
        
    private void processVirtualConnectACK(long index, int fragment, 
            int buffer) {  
            
        connectionsReplies++;
        connectionsACKs++;
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Got connect ACK: " + index + " (" + fragment + ", " 
                    + buffer + ")");
        }
        
        String key = getUniqueID(index);
        
        // It's an ACK, so we just retrieve the connection...
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout, so we send a close back to inform the 
            // sender that the connection does no longer exist...
            forwardVirtualClose(index);
            connectionsRepliesLost++;
            return; 
        }
        
        vc.setSecondBuffer(fragment);
        
        // The second connection in VC should be the one sending the ACK. Check 
        // to make sure...
        if (this == vc.mfc2) { 
            
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK for 1: " + vc.index1);
            }
            
            vc.mfc1.forwardVirtualConnectACK(vc.index1, fragment, buffer);
                    
        } else { 
            connectionsRepliesError++;
            
            // This should never happen!        
            vclogger.error("Virtual connection setup error: got ACK on wrong " +
                    "connection!", new Exception());
        }    
    }
    
    private void processVirtualConnectACKACK(long index, boolean succes) {  
            
        connectionsReplies++;
        connectionsACKs++;
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Got connect ACK ACK: " + index + ")");
        }
        
        String key = getUniqueID(index);
        
        // It's an ACK, so we just retrieve the connection...
        VirtualConnection vc = virtualConnections.find(key);
        
        if (vc == null) { 
            
         //   System.err.println("****** ACK ACK LOST " + index + " " + succes);
            
            // Connection doesn't exist. It may already be closed by the other 
            // side due to a timeout, so we send a close back to inform the 
            // sender that the connection does no longer exist...
            forwardVirtualClose(index);
            connectionsRepliesLost++;
            return; 
        }
        
        // The first connection in VC should be the one sending the ACKACK. 
        // Check to make sure...
        if (this == vc.mfc1) { 
        
            if (vclogger.isInfoEnabled()) {                                    
                vclogger.info("forward connect ACK ACK for 2: " + vc.index2);
            }
            
            vc.mfc2.forwardVirtualConnectACKACK(vc.index2, succes);
                    
        } else { 
            connectionsRepliesError++;
    
            // This should never happen!        
            vclogger.error("Virtual connection setup error: got ACK ACK on " +
                    "wrong connection!", new Exception());
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
                
                closeTotal++;
                
            } else if (this == vc.mfc2) { 
                
                if (vclogger.isInfoEnabled()) {                                    
                    vclogger.info("forward close for 1: " + vc.index1);
                }
                
                closeTotal++;
                
                vc.mfc1.forwardVirtualClose(vc.index1);
            
            } else { 
                closeError++;;
                
                // This should never happen!        
                vclogger.warn("Virtual connection error: forwarder not found!", 
                        new Exception());
            }              
        }
    }    
    
    public void printStatistics() {
     
        if (true) { 

            System.out.println(name + " ----- Connection statistics -----");
            System.out.println(name + " ");
            System.out.println(name + " Connections: " + connectionsTotal);
            System.out.println(name + "    - failed: " + connectionsFailed);
            System.out.println(name + "    - lost  : " + connectionsRepliesLost);
            System.out.println(name + "    - error : " + connectionsRepliesError);
            System.out.println(name + " ");            
            System.out.println(name + " Replies    : " + connectionsReplies);
            System.out.println(name + "  - ACK     : " + connectionsACKs);
            System.out.println(name + "  - rejected: " + connectionsNACKs);
            System.out.println(name + "  - lost    : " + connectionsRepliesLost);
            System.out.println(name + "  - error   : " + connectionsRepliesError);            
            System.out.println(name + " ");
            System.out.println(name + " Messages   : " + messages);
            System.out.println(name + "  - bytes   : " + messagesBytes);
            System.out.println(name + "  - lost    : " + messagesLost);
            System.out.println(name + "  - error   : " + messagesError);            
            System.out.println(name + " ");
            System.out.println(name + " Mess. ACKS : " + messageACK);
            System.out.println(name + "     - lost : " + messageACKLost);
            System.out.println(name + "     - error: " + messageACK_Error);   
        }
    }
    
    protected abstract boolean handleOpcode(int opcode); 
    
    protected final boolean runConnection() {
        
        try {
            int opcode = in.read();
            
            switch (opcode) { 
        
            case -1:
            case ConnectionProtocol.DISCONNECT:
                if (vclogger.isInfoEnabled()) { 
                    vclogger.info("Connection got disconnect(" + opcode + ")!");
                }
                handleDisconnect(null);
                return false;
                
            case MessageForwarderProtocol.INFO_MESSAGE:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got message!");
                }
                handleClientMessage();
                return true;
            
            case MessageForwarderProtocol.CREATE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual connect!");
                }
                
                handleCreateVirtual();
                return true;

            case MessageForwarderProtocol.CREATE_VIRTUAL_ACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual connect ACK!");
                }
               
                handleACKCreateVirtualConnection();
                return true;

            case MessageForwarderProtocol.CREATE_VIRTUAL_ACK_ACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual connect ACK ACK!");
                }
               
                handleACKACKCreateVirtualConnection();
                return true;

            case MessageForwarderProtocol.CREATE_VIRTUAL_NACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual connect NACK!");
                }
               
                handleNACKCreateVirtualConnection();
                return true;
                
            case MessageForwarderProtocol.CLOSE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual reply!");
                }
                
                handleCloseVirtual();
                return true;

            case MessageForwarderProtocol.MESSAGE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual message!");
                }
                
                handleMessageVirtual();
                return true;
            
            case MessageForwarderProtocol.MESSAGE_VIRTUAL_ACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual ack!");
                }
                
                handleMessageVirtualAck();
                return true;
            
                
            default:
                // Ask the subclass to handle this opcode!
                return handleOpcode(opcode);  
            }
                        
        } catch (Exception e) {
            handleDisconnect(e);
        }
        
        return false;
    }
    
}
