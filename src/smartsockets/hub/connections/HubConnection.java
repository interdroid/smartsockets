package smartsockets.hub.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.HubProtocol;
import smartsockets.hub.servicelink.ServiceLinkProtocol;
import smartsockets.hub.state.ClientDescription;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;
import smartsockets.hub.state.StateSelector;

public class HubConnection extends MessageForwardingConnection {

    private static final Logger conlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.hub"); 
     
    private static final Logger goslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.gossip"); 
          
    private final HubDescription peer;
    private final HubDescription local;    
    
    // Keeps the current state of the system. 
    private final StateCounter state;        
    
    // Recordes if this side did the initial connection setup.
    private final boolean master;
                         
    // Indicates the value of the local state the last time any data was send 
    // to the peer. Remembering this allows us to send delta's. 
    private long lastSendState;    
            
    public HubConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, HubDescription peer, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList proxies, StateCounter state, boolean master) {
        
        super(s, in, out, connections, proxies);
        
        this.peer = peer;        
        this.state = state;
        this.master = master;
        
        local = proxies.getLocalDescription();
    }
        
    protected String incomingVirtualConnection(VirtualConnection origin, 
            MessageForwardingConnection m, SocketAddressSet source, 
            SocketAddressSet target, String info, int timeout) {
        
        // Incoming request for a virtual connection to the hub connected to 
        // this HubConnection....
        String id = getID();

        // Register the fact that we expect a reply for this id.
        replyPending(id);
        
        VirtualConnection vc = vcs.newVC(master);
        
        // Send the connect request to the hub
        try { 
            synchronized (this) {
                out.write(HubProtocol.CREATE_VIRTUAL);           
                out.writeUTF(id);            
                out.writeInt(vc.index);
                out.writeUTF(source.toString());
                out.writeUTF(target.toString());
                out.writeUTF(info);            
                out.writeInt(timeout);            
                out.flush();
            }
        } catch (Exception e) {
            vclogger.warn("Connection to " + peer + " is broken!", e);
            // TODO: close connection ????
            return "Lost connection to target";
        }
        
        // Wait for the reply. This will remove the id before it returns, even 
        // if no reply was received (this is needed to discover that the client  
        // has already left before the server has replied). 
        String [] reply = waitForReply(id, timeout);
        
        if (reply == null) {
            // receiver took too long to accept: timeout
            vcs.freeVC(vc);            
            return "Timeout";
        }
        
        if (reply.length != 2) {
            // got malformed reply
            vcs.freeVC(vc);
            return "Received malformed reply";
        }
        
        if (reply[0].equals("DENIED")) { 
            // connection refused
            vcs.freeVC(vc);
            return reply[1];
        }

        if (!reply[0].equals("OK")) {
            // should be DENIED or OK, so we got a malformed reply
            vcs.freeVC(vc);
            return "Received malformed reply";
        }

        // Now tie the two together!
        vc.init(m, origin.index, 0);
        origin.init(this, vc.index, 0);
        
        vclogger.warn("HUB Connection setup of: " + origin.index 
                + "<-->" + vc.index);
                
        // return null to indicate the lack of errors ;-)
        return null;
    }
    
    protected void forwardVirtualClose(int index) { 
        
        vclogger.warn("HUB Sending closing connection: " + index);
        
        // forward the close
        try {
            synchronized (this) {
                out.write(HubProtocol.CLOSE_VIRTUAL);           
                out.writeInt(index);            
                out.flush();
            }
        } catch (Exception e) {
            conlogger.warn("Connection to " + peer.hubAddressAsString 
                    + " is broken!", e);
            
//          TODO: handle exception...
        }                
    }
    
    protected void forwardVirtualMessage(int index, byte [] data) {
        
        // forward the message
        try {
            synchronized (this) {
                out.write(HubProtocol.MESSAGE_VIRTUAL);           
                out.writeInt(index);
                out.writeInt(data.length);
                out.write(data);
                out.flush();                
            }
        } catch (Exception e) {
            conlogger.warn("Connection to " + peer.hubAddressAsString 
                    + " is broken!", e);
            
            // TODO: handle exception...
        }        
    }
    
    protected void forwardVirtualMessageAck(int index) { 

        // forward the message
        try {
            synchronized (this) {
                out.write(ServiceLinkProtocol.MESSAGE_VIRTUAL_ACK);           
                out.writeInt(index);
                out.flush();                
            }
        } catch (Exception e) {
            conlogger.warn("Connection to " + peer.hubAddressAsString 
                    + " is broken!", e);
            
            // TODO: handle exception...
        }        
    }
    
    public synchronized void setLastSendState() {
        lastSendState = state.get();
    }
    
    public synchronized void writeMessage(ClientMessage m) { 
        
        try {
            out.writeByte(HubProtocol.CLIENT_MESSAGE);            
            m.write(out);            
            out.flush();
        } catch (IOException e) {
            meslogger.warn("Unhandled exception in writeMessage!!", e);            
            // TODO: handle exception
        }        
    }
    
    public void gossip() { 
        
        long newSendState = state.get(); 

        if (goslogger.isInfoEnabled()) {             
            goslogger.info("Gossiping with: " + peer.hubAddress);
        }
        
        StateSelector ss = new StateSelector(lastSendState);
        
        knownHubs.select(ss);
        
        try {
            int writes = 0;

            for (HubDescription tmp : ss.getResult()) { 
                
                if (goslogger.isInfoEnabled()) { 
                    goslogger.info("    Writing proxy: " + tmp.hubAddressAsString);
                }
                
                if (goslogger.isDebugEnabled()) {
                    goslogger.debug("      since lastLocalUpdate="  
                            + tmp.getLastLocalUpdate()  
                            + " > lastSendState= " + lastSendState);
                }
           
                synchronized (this) {  
                    writeHub(tmp);
                } 
                writes++;
            }        
            
            if (writes == 0) {
                // No proxies where written, so write a ping instead.
                synchronized (this) { 
                    writePing();
                } 
            } 
            
            synchronized (this) { 
                out.flush();
            }
            
        } catch (Exception e) {
            goslogger.warn("Unhandled exception in HubConnection!!", e);            
        }
        
        lastSendState = newSendState;
        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {        
        out.write(HubProtocol.PING);
    } 
    
    private void writeHub(HubDescription d) throws IOException {        
        out.write(HubProtocol.GOSSIP);
        
        out.writeUTF(d.hubAddress.toString());
        out.writeUTF(d.getName());
        out.writeInt(d.getHops());
        
        if (d.isLocal()) { 
            out.writeLong(d.getLastLocalUpdate());
        } else { 
            out.writeLong(d.getHomeState());
        } 
        
        ArrayList<ClientDescription> clients = d.getClients(null);        
        
        out.writeInt(clients.size()); 

        for (ClientDescription c : clients) {
            c.write(out);
        }    
        
        String [] connectedTo = d.connectedTo();
        
        if (connectedTo == null || connectedTo.length == 0) {         
            out.writeInt(0);
            return;
        }  
          
        out.writeInt(connectedTo.length);
        
        for (String c : connectedTo) { 
            out.writeUTF(c);
        }    
    } 
        
    private void readProxy() throws IOException {
                
        SocketAddressSet address = new SocketAddressSet(in.readUTF());        
        String name = in.readUTF();
        
        HubDescription tmp = knownHubs.add(address);
               
        int hops = in.readInt();
        
        long state = in.readLong();
        
        int clients = in.readInt();        
        
        ClientDescription [] c = new ClientDescription[clients];
        
        for (int i=0;i<clients;i++) {
            c[i] = ClientDescription.read(in);                        
        }
        
        int conns = in.readInt();        
                
        String [] a = new String[conns];
        
        for (int i=0;i<conns;i++) {
            a[i] = in.readUTF();                        
        }
                        
        if (local.hubAddress.equals(address)) {
            // Just received information about myself!
            if (hops == 0) {
                peer.setCanReachMe();
            } else { 
                peer.setCanNotReachMe();
            }
        } else if (tmp == peer) {
            // The peer send information about itself. This should 
            // always be up-to-date.
            if (state > tmp.getHomeState()) { 
                tmp.update(c, a, name, state);
            } else { 
                goslogger.warn("EEK: got information directly from " 
                        + peer.hubAddressAsString 
                        + (name.length() > 0 ? (" (" + name + ")") : "") 
                        + " which seems to be out of date! " + state 
                        + " " + tmp.getHomeState());
            }
        } else {
            // We got information about a 'third party'.               
            if (hops+1 < tmp.getHops()) {
                // We seem to have found a shorter route to the target
                tmp.addIndirection(peer, hops+1);
            } 
            
            // Check if the information is more recent than what I know...
            if (state > tmp.getHomeState()) { 
                tmp.update(c, a, name, state);
            } else {
                String pn = peer.getName();
        
                if (goslogger.isDebugEnabled()) {
                    goslogger.debug("Ignoring outdated information about " 
                        + tmp.hubAddressAsString 
                        + (name.length() > 0 ? (" (" + name + ")") : "")
                        + " from " + peer.hubAddressAsString 
                        + (pn.length() > 0 ? (" (" + pn + ") ") : " ")                        
                        + state + " " 
                        + tmp.getHomeState());
                }
            }
        }
        
        peer.setContactTimeStamp(false);
    }
        
    private void handlePing() {
        if (goslogger.isInfoEnabled()) {
            goslogger.debug("Got ping from " + peer.hubAddress);
        }
        
        peer.setContactTimeStamp(false);
    }
  
    private void handleClientMessage() throws IOException {        
        ClientMessage cm = new ClientMessage(in);        
        
        if (meslogger.isDebugEnabled()) {
            meslogger.debug("Got message: " + cm);
        }
        
        forward(cm, false);          
    }
    
    private void handleCreateVirtual() throws IOException { 
    
        String id = in.readUTF();
        
        int index = in.readInt();
        
        String source = in.readUTF();
        String target = in.readUTF();        
        String info = in.readUTF();        
        
        int timeout = in.readInt();
        
        vclogger.warn("HUB connection request for: " + index);
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Connection " + peer.hubAddressAsString 
                    + " return id: " + id + " creating virtual connection " 
                    + source + " -->> " + target);
        }
        
        String result = createVirtualConnection(index, 
                new SocketAddressSet(source), new SocketAddressSet(target), 
                info, timeout);
    
        synchronized (this) {
            out.write(HubProtocol.REPLY_VIRTUAL);           
            out.writeUTF(id);            
            
            if (result == null) { 
                out.writeUTF("OK");
                out.writeInt(index);                
            } else { 
                out.writeUTF("DENIED");
                out.writeUTF(result);
            }
            
            out.flush();
        }         
    }
        
    private void handleReplyVirtual() throws IOException { 
        
        String localID = in.readUTF();        
        String result = in.readUTF();
        
        if (result.equals("DENIED")) { 
            String error = in.readUTF();        
            
            // This store may fail, but we don't care since we don't have any 
            // state on the other side anyway...
            storeReply(localID, new String [] { result, error });
           
        } else if (result.equals("OK")) {
            
            int index = in.readInt();
            
            if (!storeReply(localID, new String [] { result, "" })) {
                // We are too late, the client has already left...
                // So we just do a disconnect for the rest of the path...
                synchronized (this) {
                    out.writeByte(HubProtocol.CLOSE_VIRTUAL);
                    out.writeInt(index);
                    out.flush();
                }
            }           
        } else { 
            vclogger.warn("HubConnection got junk in handleReplyVirtual!: " 
                    + result);
            
            // TODO: close connection ?             
        }
    }     

    private void handleCloseVirtual() throws IOException { 
        
        int index = in.readInt();     
        
        vclogger.warn("HUB locally closing connection: " + index);
        
        String result = closeVirtualConnection(index);
        
        if (result != null) { 
            vclogger.warn("Failed to close connection " + index + ": " + result);
        }
    } 
 
    private void handleMessageVirtual() throws IOException { 
        
        int vc = in.readInt();
        int size = in.readInt();
    
        // TODO: optimize!
        byte [] data = new byte[size];        
        in.readFully(data);
                      
        forwardMessage(vc, data);
    }
    
    private void handleMessageVirtualAck() throws IOException {
        forwardMessageAck(in.readInt());
    }
        
    
    protected String getName() { 
        return "HubConnection(" + peer.hubAddress + ")";
    }
    
    private void disconnect() {
                
        // Update the administration
        connections.remove(peer.hubAddress);        
        local.removeConnectedTo(peer.hubAddressAsString);
        
        DirectSocketFactory.close(s, out, in);            
    } 
    
    protected boolean runConnection() {
    
        try { 
            int opcode = in.read();
            
            switch (opcode) { 
        
            case -1:
                if (conlogger.isInfoEnabled()) { 
                    conlogger.info("HubConnection got EOF!");
                }
                disconnect();
                return false;
                
            case HubProtocol.GOSSIP:
                if (goslogger.isInfoEnabled()) {
                    goslogger.info("HubConnection got gossip!");
                }
                readProxy();
                return true;
    
            case HubProtocol.PING:
                if (goslogger.isInfoEnabled()) {
                    goslogger.info("HubConnection got ping!");
                }
                handlePing();
                return true;

            case HubProtocol.CLIENT_MESSAGE:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got message!");
                }
                handleClientMessage();
                return true;
            
            case HubProtocol.CREATE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual connect!");
                }
                
                handleCreateVirtual();
                return true;

            case HubProtocol.REPLY_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual reply!");
                }
                
                handleReplyVirtual();
                return true;

            case HubProtocol.CLOSE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual reply!");
                }
                
                handleCloseVirtual();
                return true;

            case HubProtocol.MESSAGE_VIRTUAL:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual message!");
                }
                
                handleMessageVirtual();
                return true;
            
            case HubProtocol.MESSAGE_VIRTUAL_ACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual ack!");
                }
                
                handleMessageVirtualAck();
                return true;
            
                
            default:
                conlogger.warn("HubConnection got junk!");                   
                disconnect();
                return false;
            }
                        
        } catch (Exception e) {
            conlogger.warn("HubConnection got exception!", e);
            disconnect();
        }
        
        return false;
    }
}
