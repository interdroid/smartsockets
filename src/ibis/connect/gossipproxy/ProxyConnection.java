package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

class ProxyConnection implements Runnable {

    private final VirtualSocket s;
    private final DataInputStream in;
    private final DataOutputStream out; 
    
    private final ProxyDescription peer;
    private final ProxyDescription local;    
            
    private final ProxyList knownProxies; 
    private final StateCounter state;
    
    private boolean done = false;
          
    ProxyConnection(VirtualSocket s, DataInputStream in, DataOutputStream out, 
            ProxyDescription peer, ProxyList knownProxies, StateCounter state) {
        
        this.s = s;
        this.in = in;
        this.out = out;
        this.peer = peer;
        this.knownProxies = knownProxies;
        this.state = state;
        
        local = knownProxies.getLocalDescription();
    }
    
    public void writeProxies(long currentState) { 
        
        long lastSendState = peer.getLastSendState();
        
        try {
            int writes = 0;

            GossipProxy.logger.info("=============================="); 
            GossipProxy.logger.info("Gossiping with: " + peer.proxyAddress); 
            
            Iterator itt = knownProxies.iterator();
            
            while (itt.hasNext()) { 
                ProxyDescription tmp = (ProxyDescription) itt.next();
                
                if (tmp.getLastLocalUpdate() > lastSendState) {
                    
                    GossipProxy.logger.info("Writing proxy:\n" 
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
        
            GossipProxy.logger.info("==============================\n");
            
        } catch (Exception e) {
            // TODO: handle exception
        }
        
        peer.setLastSendState();        
        peer.setContactTimeStamp(false);        
    }
    
    private void writePing() throws IOException {        
        System.err.println("Sending ping to " + peer.proxyAddress);
        out.write(Protocol.PROXY_PING);
    } 
    
    private void writeProxy(ProxyDescription d) throws IOException {        
        out.write(Protocol.PROXY_GOSSIP);
        out.writeUTF(d.proxyAddress.toString());
        out.writeInt(d.getHops());
        
        // TODO: fix race condition here!
        int size = d.clients.size();        
        out.writeInt(size); 

        for (int i=0;i<size;i++) {         
            out.writeUTF(d.proxyAddress.toString());
        } 
    } 
        
    private void readProxy() throws IOException {
                
        VirtualSocketAddress address = new VirtualSocketAddress(in.readUTF());                
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
                peer.addClient(c[i]);
            }  
        } else {
            // We got information about a 'third party'.             
            if (hops+1 < tmp.getHops()) {
                // We seem to have found a shorter route to the target
                tmp.addIndirection(peer.proxyAddress, hops+1);
            } 
            
            for (int i=0;i<clients;i++) { 
                peer.addClient(c[i]);
            }  
        }
        
        peer.setContactTimeStamp(false);
    }
        
    private void handlePing() {
        System.err.println("Got ping from " + peer.proxyAddress);
        peer.setContactTimeStamp(false);
    }
    
    private void receive() {
    
        try { 
            int opcode = in.read();
            
            switch (opcode) { 
        
            case -1:
                // Connection died ? 
                break; 
            
            case Protocol.PROXY_GOSSIP:
                readProxy();
                break;
    
            case Protocol.PROXY_PING:
                handlePing();
                break;
                                
            default:
            
            }
                        
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    public void activate() {
        // TODO: Use pool ?         
        new Thread(this, "ProxyConnection").start();
    }
        
    public void run() { 

        while (!done) { 
            receive();
        }
    }
}
