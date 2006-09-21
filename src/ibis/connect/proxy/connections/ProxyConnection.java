package ibis.connect.proxy.connections;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.proxy.ProxyProtocol;
import ibis.connect.proxy.state.ClientDescription;
import ibis.connect.proxy.state.ProxyDescription;
import ibis.connect.proxy.state.ProxyList;
import ibis.connect.proxy.state.StateCounter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class ProxyConnection extends MessageForwardingConnection {

    private final ProxyDescription peer;
    private final ProxyDescription local;    
    
    // Keeps the current state of the system. 
    private final StateCounter state;        
                         
    // Indicates the value of the local state the last time any data was send 
    // to the peer. Remembering this allows us to send delta's. 
    private long lastSendState;    
            
    public ProxyConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, ProxyDescription peer, 
            Connections connections, ProxyList proxies, StateCounter state) {
        
        super(s, in, out, connections, proxies);
        
        this.peer = peer;        
        this.state = state;
        local = proxies.getLocalDescription();
    }
        
    public synchronized void setLastSendState() {
        lastSendState = state.get();
    }
    
    public synchronized void writeMessage(String source, String target, 
            String module, int code, String message, int hopsLeft) { 
        
        try {
            out.writeByte(ProxyProtocol.CLIENT_MESSAGE);
            
            out.writeUTF(source);
            out.writeUTF(target);
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

            logger.info("=============================="); 
            logger.info("Gossiping with: " + peer.proxyAddress); 
            
            Iterator itt = knownProxies.iterator();
            
            while (itt.hasNext()) { 
                ProxyDescription tmp = (ProxyDescription) itt.next();
                
                if (tmp.getLastLocalUpdate() > lastSendState) {
                    
                    logger.info("Writing proxy:\n" 
                            + tmp.proxyAddressAsString
                            + " since lastLocalUpdate=" 
                            + tmp.getLastLocalUpdate()  
                            + " > lastSendState= " + lastSendState + "\n\n");
                    
                    writeProxy(tmp);                    
                    writes++;
                } else { 
                    logger.info("NOT writing proxy:\n"
                            + tmp.proxyAddressAsString 
                            + " since lastLocalUpdate=" 
                            + tmp.getLastLocalUpdate()  
                            + " <= lastSendState= " + lastSendState + "\n\n");                    
                }
            }        
            
            if (writes == 0) {
                // No proxies where written, so write a ping instead.                 
                writePing();
            } 
            
            out.flush();
        
            logger.info("==============================\n");
            
        } catch (Exception e) {
            System.err.println("Unhandled exception in ProxyConnection!!" + e);
            // TODO: handle exception
        }
        
        lastSendState = newSendState;
        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {        
        System.err.println("Sending ping to " + peer.proxyAddress);
        out.write(ProxyProtocol.PING);
    } 
    
    private void writeProxy(ProxyDescription d) throws IOException {        
        out.write(ProxyProtocol.GOSSIP);
        
        out.writeUTF(d.proxyAddress.toString());
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
        ProxyDescription tmp = knownProxies.add(address);
               
        int hops = in.readInt();
        
        long state = in.readLong();
        
        int clients = in.readInt();        
        
        ClientDescription [] c = new ClientDescription[clients];
        
        for (int i=0;i<clients;i++) {
            c[i] = ClientDescription.read(in);                        
        }
                        
        if (local.proxyAddress.equals(address)) {
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
                logger.warn("EEK: got information directly from " 
                        + peer.proxyAddressAsString + " which seems to be "
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
                logger.info("Ignoring outdated information about " + 
                        tmp.proxyAddressAsString + " from "  
                        + peer.proxyAddressAsString + " " + state + " " 
                        + tmp.getHomeState());                
            }
        }
        
        peer.setContactTimeStamp(false);
    }
        
    private void handlePing() {        
        logger.debug("Got ping from " + peer.proxyAddress);
        peer.setContactTimeStamp(false);
    }
  
    private void handleClientMessage() throws IOException {
        
        String source = in.readUTF();
        String target = in.readUTF();
        String module = in.readUTF();
        int code = in.readInt();
        String message = in.readUTF();
        int hopsLeft = in.readInt();
        
        logger.debug("Got message [" + source + ", " 
                + target + ", " + module + ", " + code + ", " + message 
                + ", " + hopsLeft + "]");
               
        forwardMessage(source, target, module, code, message, hopsLeft);          
    }
    
    protected String getName() { 
        return "ProxyConnection(" + peer.proxyAddress + ")";
    }
    
    protected boolean runConnection() {
    
        try { 
            int opcode = in.read();
            
            switch (opcode) { 
        
            case -1:
                logger.info("ProxyConnection got EOF!");
                DirectSocketFactory.close(s, out, in);
                return false;
                
            case ProxyProtocol.GOSSIP:
                readProxy();
                return true;
    
            case ProxyProtocol.PING:
                handlePing();
                return true;

            case ProxyProtocol.CLIENT_MESSAGE:
                handleClientMessage();
                return true;
               
            default:
                logger.info("ProxyConnection got junk!");
                DirectSocketFactory.close(s, out, in);
                return false;
            }
                        
        } catch (Exception e) {
            logger.warn("ProxyConnection got exception!", e);
            DirectSocketFactory.close(s, out, in);
        }
        
        return false;
    }
}
