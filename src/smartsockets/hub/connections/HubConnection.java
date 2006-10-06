package smartsockets.hub.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.HubProtocol;
import smartsockets.hub.state.ClientDescription;
import smartsockets.hub.state.HubDescription;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

public class HubConnection extends MessageForwardingConnection {

    protected static Logger conlogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.connections.hub"); 
    
    protected static Logger goslogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.gossip"); 
          
    private final HubDescription peer;
    private final HubDescription local;    
    
    // Keeps the current state of the system. 
    private final StateCounter state;        
                         
    // Indicates the value of the local state the last time any data was send 
    // to the peer. Remembering this allows us to send delta's. 
    private long lastSendState;    
            
    public HubConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, HubDescription peer, 
            Connections connections, HubList proxies, StateCounter state) {
        
        super(s, in, out, connections, proxies);
        
        this.peer = peer;        
        this.state = state;
        local = proxies.getLocalDescription();
    }
        
    public synchronized void setLastSendState() {
        lastSendState = state.get();
    }
    
    public synchronized void writeMessage(String source, String sourceProxy,  
            String target, String targetProxy, String module, int code, 
            String message, int hopsLeft) { 
        
        if (targetProxy == null) { 
            targetProxy = "";
        }
                
        try {
            out.writeByte(HubProtocol.CLIENT_MESSAGE);
            
            out.writeUTF(source);
            out.writeUTF(sourceProxy);
        
            out.writeUTF(target);
            out.writeUTF(targetProxy);
            
            out.writeUTF(module);
            out.writeInt(code);
            out.writeUTF(message);
            out.writeInt(hopsLeft);
            out.flush();
        } catch (IOException e) {
            System.err.println("Unhandled exception in writeMessage!!" + e);            
            // TODO: handle exception
        }        
    }
    
    public synchronized void gossip(long currentState) { 
        
        long newSendState = state.get(); 
        
        try {
            int writes = 0;

            goslogger.info("Gossiping with: " + peer.hubAddress); 
            
            Iterator itt = knownHubs.iterator();
            
            while (itt.hasNext()) { 
                HubDescription tmp = (HubDescription) itt.next();
                
                if (tmp.getLastLocalUpdate() > lastSendState) {
                    
                    goslogger.info("    Writing proxy: " + tmp.hubAddressAsString);                    
                    goslogger.debug("      since lastLocalUpdate="  
                            + tmp.getLastLocalUpdate()  
                            + " > lastSendState= " + lastSendState);
                    
                    writeProxy(tmp);                    
                    writes++;
                } else { 
                    goslogger.info("    NOT writing proxy: " + tmp.hubAddressAsString);                    
                    goslogger.debug("      since lastLocalUpdate="  
                            + tmp.getLastLocalUpdate()  
                            + " <= lastSendState= " + lastSendState);
                }
            }        
            
            if (writes == 0) {
                // No proxies where written, so write a ping instead.                 
                writePing();
            } 
            
            out.flush();
            
        } catch (Exception e) {
            goslogger.warn("Unhandled exception in ProxyConnection!!" + e);
            // TODO: handle exception
        }
        
        lastSendState = newSendState;
        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {        
        out.write(HubProtocol.PING);
    } 
    
    private void writeProxy(HubDescription d) throws IOException {        
        out.write(HubProtocol.GOSSIP);
        
        out.writeUTF(d.hubAddress.toString());
        out.writeInt(d.getHops());
        
        if (d.isLocal()) { 
            out.writeLong(d.getLastLocalUpdate());
        } else { 
            out.writeLong(d.getHomeState());
        } 
        
        ArrayList clients = d.getClients(null);        
        out.writeInt(clients.size()); 

        for (int i=0;i<clients.size();i++) {                 
            ClientDescription.write((ClientDescription) clients.get(i), out);            
        }    
    } 
        
    private void readProxy() throws IOException {
                
        SocketAddressSet address = new SocketAddressSet(in.readUTF());
        HubDescription tmp = knownHubs.add(address);
               
        int hops = in.readInt();
        
        long state = in.readLong();
        
        int clients = in.readInt();        
        
        ClientDescription [] c = new ClientDescription[clients];
        
        for (int i=0;i<clients;i++) {
            c[i] = ClientDescription.read(in);                        
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
                tmp.update(c, state);
            } else { 
                goslogger.warn("EEK: got information directly from " 
                        + peer.hubAddressAsString + " which seems to be "
                        + "out of date! " + state + " " + tmp.getHomeState());
            }
        } else {
            // We got information about a 'third party'.               
            if (hops+1 < tmp.getHops()) {
                // We seem to have found a shorter route to the target
                tmp.addIndirection(peer, hops+1);
            } 
            
            // Check if the information is more recent than what I know...
            if (state > tmp.getHomeState()) { 
                tmp.update(c, state);
            } else {
                goslogger.debug("Ignoring outdated information about " + 
                        tmp.hubAddressAsString + " from "  
                        + peer.hubAddressAsString + " " + state + " " 
                        + tmp.getHomeState());                
            }
        }
        
        peer.setContactTimeStamp(false);
    }
        
    private void handlePing() {        
        goslogger.debug("Got ping from " + peer.hubAddress);
        peer.setContactTimeStamp(false);
    }
  
    private void handleClientMessage() throws IOException {
        
        String source = in.readUTF();
        String sourceProxy = in.readUTF();
        
        String target = in.readUTF();
        String targetProxy = in.readUTF();
        
        String module = in.readUTF();
        int code = in.readInt();
        
        String message = in.readUTF();
        int hopsLeft = in.readInt();
        
        meslogger.debug("Got message [" + source + "@" + sourceProxy + ", " 
                + target  + "@" + targetProxy + ", " + module + ", " + code 
                + ", " + message + ", " + hopsLeft + "]");
               
        forward(source, sourceProxy, target, targetProxy, module, code, message,
                hopsLeft);          
    }
    
    protected String getName() { 
        return "ProxyConnection(" + peer.hubAddress + ")";
    }
    
    protected boolean runConnection() {
    
        try { 
            int opcode = in.read();
            
            switch (opcode) { 
        
            case -1:
                conlogger.info("HubConnection got EOF!");
                DirectSocketFactory.close(s, out, in);
                return false;
                
            case HubProtocol.GOSSIP:
                goslogger.info("HubConnection got gossip!");                
                readProxy();
                return true;
    
            case HubProtocol.PING:
                goslogger.info("HubConnection got ping!");                                
                handlePing();
                return true;

            case HubProtocol.CLIENT_MESSAGE:
                meslogger.info("HubConnection got message!");                                                
                handleClientMessage();
                return true;
               
            default:
                conlogger.warn("HubConnection got junk!");
                DirectSocketFactory.close(s, out, in);
                return false;
            }
                        
        } catch (Exception e) {
            conlogger.warn("HubConnection got exception!", e);
            DirectSocketFactory.close(s, out, in);
        }
        
        return false;
    }
}
