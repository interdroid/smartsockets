package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class ProxyConnection extends BaseConnection {

    private final ProxyDescription peer;
    private final ProxyDescription local;    
                      
    ProxyConnection(DirectSocket s, DataInputStream in, DataOutputStream out, 
            ProxyDescription peer, Connections connections, ProxyList proxies) {
        
        super(s, in, out, connections, proxies);
        
        this.peer = peer;        
        local = proxies.getLocalDescription();
    }
    
    public synchronized void writeMessage(String source, String target, 
            String module, int code, String message) { 
        
        try { 
            out.writeByte(ProxyProtocol.CLIENT_MESSAGE);
            out.writeUTF(source);
            out.writeUTF(target);
            out.writeUTF(module);
            out.writeInt(code);
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Unhandled exception in writeMessage!!" + e);            
            // TODO: handle exception
        }        
    }
    
    public synchronized void gossip(long currentState) { 
        
        long lastSendState = peer.getLastSendState();
        
        try {
            int writes = 0;

            logger.info("=============================="); 
            logger.info("Gossiping with: " + peer.proxyAddress); 
            
            Iterator itt = knownProxies.iterator();
            
            while (itt.hasNext()) { 
                ProxyDescription tmp = (ProxyDescription) itt.next();
                
                if (tmp.getLastLocalUpdate() > lastSendState) {
                    
                    logger.info("Writing proxy:\n" 
                            + tmp.toString() + "\n\n");
                    
                    writeProxy(tmp);                    
                    writes++;
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
        
        peer.setLastSendState();        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {        
        System.err.println("Sending ping to " + peer.proxyAddress);
        out.write(ProxyProtocol.PROXY_PING);
    } 
    
    private void writeProxy(ProxyDescription d) throws IOException {        
        out.write(ProxyProtocol.PROXY_GOSSIP);
        
        out.writeUTF(d.proxyAddress.toString());
        out.writeInt(d.getHops());

        ArrayList clients = d.getClients();        
        out.writeInt(clients.size()); 

        for (int i=0;i<clients.size();i++) {         
            out.writeUTF((String) clients.get(i));
        }    
    } 
        
    private void readProxy() throws IOException {
                
        SocketAddressSet address = new SocketAddressSet(in.readUTF());                
        ProxyDescription tmp = knownProxies.add(address);
               
        int hops = in.readInt();
        
        int clients = in.readInt();
        String [] c = new String[clients];
        
        for (int i=0;i<clients;i++) {  
            c[i] = in.readUTF();
        }
                        
        if (local.proxyAddress.equals(address)) {
            // Just received information about myself!
            if (hops == 0) {
                peer.setCanReachMe();
            } else { 
                peer.setCanNotReachMe();
            }
        } else if (tmp == peer) {
            // The peer send information about itself. 
            for (int i=0;i<clients;i++) { 
                tmp.addClient(c[i]);
            }  
        } else {
            // We got information about a 'third party'.              
            if (hops+1 < tmp.getHops()) {
                // We seem to have found a shorter route to the target
                tmp.addIndirection(peer.proxyAddress, hops+1);
            } 
            
            for (int i=0;i<clients;i++) { 
                tmp.addClient(c[i]);
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

        logger.debug("Got client message [" + source + ", " 
                + target + ", " + module + ", " + code + ", " + message 
                + "] NOT FORWARDED YET!!!");   
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
                
            case ProxyProtocol.PROXY_GOSSIP:
                readProxy();
                return true;
    
            case ProxyProtocol.PROXY_PING:
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