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
                      
    // Indicates the value of the local state the last time any data was send 
    // to the peer. Remembering this allows us to send delta's. 
    private long lastSendState;    
            
    private final String uniquePrefix;
    
    public HubConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, HubDescription peer, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList proxies, StateCounter state, VirtualConnections vcs, 
            boolean master) {
        
        super(s, in, out, connections, proxies, vcs, master);
        
        this.peer = peer;        
        this.state = state;
        
        this.uniquePrefix = peer.hubAddressAsString + "__";
        
        local = proxies.getLocalDescription();
    }
         
    protected String getUniqueID(long index) { 
        return uniquePrefix + index;
    }
    
    protected void forwardVirtualConnect(SocketAddressSet source, 
            SocketAddressSet target, SocketAddressSet targetHub, String info, 
            int timeout, long index) { 
        
        // TODO: Should be asynchronous ???
        
        // Send the connect request to the hub
        try { 
            synchronized (out) {
                out.write(HubProtocol.CREATE_VIRTUAL);           
                out.writeLong(index);
                out.writeUTF(source.toString());
                out.writeUTF(target.toString());
                
                if (targetHub != null) { 
                    out.writeUTF(targetHub.toString());
                } else { 
                    out.writeUTF("");            
                }
                
                out.writeUTF(info);            
                out.writeInt(timeout);            
                out.flush();
            }
        } catch (Exception e) {
            vclogger.warn("Connection to " + peer + " is broken!", e);
            // TODO: close connection ????
        }
    }
    
    protected void forwardVirtualConnectAck(long index, String result, 
            String info) { 
        
        // TODO: Should be asynchronous ???
        
        // Send the connect request to the hub
        try { 
            synchronized (out) {
                out.write(HubProtocol.CREATE_VIRTUAL_ACK);           
                out.writeLong(index);
                out.writeUTF(result);
                out.writeUTF(info);            
                out.flush();
            }
        } catch (Exception e) {
            vclogger.warn("Connection to " + peer + " is broken!", e);
            // TODO: close connection ????
        }
    } 
    
    protected void forwardVirtualClose(long index) { 
    
        // TODO: Should be asynchronous ???
        
        if (vclogger.isInfoEnabled()) { 
            vclogger.info("HUB Sending closing connection: " + index);
        }
        // forward the close
        try {
            synchronized (out) {
                out.write(HubProtocol.CLOSE_VIRTUAL);           
                out.writeLong(index);            
                out.flush();
            }
        } catch (Exception e) {
            conlogger.warn("Connection to " + peer.hubAddressAsString 
                    + " is broken!", e);
            
//          TODO: handle exception...
        }                
    }
    
    protected void forwardVirtualMessage(long index, byte [] data) {
       
        // TODO: Should be asynchronous ???
        
        // forward the message
        try {
            synchronized (out) {
                out.write(HubProtocol.MESSAGE_VIRTUAL);           
                out.writeLong(index);
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
    
    protected void forwardVirtualMessageAck(long index) { 

        // TODO: Should be asynchronous ???
        
        // forward the message
        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.MESSAGE_VIRTUAL_ACK);           
                out.writeLong(index);
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
                
                writeHub(tmp);
                writes++;
            }        
            
            if (writes == 0) {
                // No proxies where written, so write a ping instead.
                writePing();
            } 
            
            synchronized (out) { 
                out.flush();
            }
            
        } catch (Exception e) {
            goslogger.warn("Unhandled exception in HubConnection!!", e);            
        }
        
        lastSendState = newSendState;
        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {      
        synchronized (out) {
            out.write(HubProtocol.PING);
        }
    } 
    
    private void writeHub(HubDescription d) throws IOException {  
        
        synchronized (out) {
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
    
        long index = in.readLong();
        
        String source = in.readUTF();
        String target = in.readUTF();   
        String targetHub = in.readUTF();  
        String info = in.readUTF();        
        
        int timeout = in.readInt();
        
        if (vclogger.isInfoEnabled()) {                                    
            vclogger.info("HUB connection request for: " + index);
        }
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Connection " + peer.hubAddressAsString 
                    + " creating virtual connection " 
                    + source + " -->> " + target);
        }
        
        SocketAddressSet hub = null;
        
        if (targetHub.length() > 0) { 
            try { 
                hub = new SocketAddressSet(targetHub);
            } catch (Exception e) {
                // ignore -- not critical
            }
        }
        
        processVirtualConnect(index, new SocketAddressSet(source), 
                new SocketAddressSet(target), hub, info, timeout);
    }
         
    private void handleCloseVirtual() throws IOException { 
        
        long index = in.readLong();     
        
        if (vclogger.isInfoEnabled()) {                        
            vclogger.info("HUB locally closing connection: " + index);
        }
        
        closeVirtualConnection(index);  
    } 
 
    private void handleMessageVirtual() throws IOException { 
        
        long vc = in.readLong();
        int size = in.readInt();
    
        // TODO: optimize!
        byte [] data = new byte[size];        
        in.readFully(data);
                      
        processMessage(vc, data);
    }
    
    private void handleMessageVirtualAck() throws IOException {
        processMessageACK(in.readLong());
    }
        
    private void handleAckCreateVirtualConnection() throws IOException { 
        
        long index = in.readLong();
        String result = in.readUTF();
        String info = in.readUTF();
        
        if (vclogger.isDebugEnabled()) {
            vclogger.debug("Got reply to VC: " + index + " " + result + " " + info);
        }
    
        processVirtualConnectACK(index, result, info);
    } 
    
    protected String getName() { 
        return "HubConnection(" + peer.hubAddress + ")";
    }
    
    private void disconnect() {
                
        // Update the administration
        connections.remove(peer.hubAddress);        
        local.removeConnectedTo(peer.hubAddressAsString);
        
        DirectSocketFactory.close(s, out, in);            
    
        closeAllVirtualConnections(uniquePrefix);
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

            case HubProtocol.CREATE_VIRTUAL_ACK:
                if (meslogger.isInfoEnabled()) {
                    meslogger.info("HubConnection got virtual reply!");
                }
               
                handleAckCreateVirtualConnection();
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
