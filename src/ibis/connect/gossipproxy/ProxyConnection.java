package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

class ProxyConnection implements Runnable {

    private final VirtualSocket s;
    private final DataInputStream in;
    private final DataOutputStream out; 
    private final ProxyDescription peer;    
    private final ProxyList knownProxies; 
    
    private int lastWrittenState = 0;    
    private boolean done = false;
          
    ProxyConnection(VirtualSocket s, DataInputStream in, DataOutputStream out, 
            ProxyDescription peer, ProxyList knownProxies) { 
        this.s = s;
        this.in = in;
        this.out = out;
        this.peer = peer;
        this.knownProxies = knownProxies;
    }
    
    public void writeProxies(int currentState) { 
        
        if (currentState <= lastWrittenState) {
            return;
        } 
        
        try { 
            Iterator itt = knownProxies.iterator();
        
            while (itt.hasNext()) { 
                ProxyDescription tmp = (ProxyDescription) itt.next();            
                writeProxy(tmp);
            }        
            
            out.flush();           
        } catch (Exception e) {
            // TODO: handle exception
        }
        
        lastWrittenState = currentState;
    }
    
    private void writeProxy(ProxyDescription d) throws IOException { 
        out.write(Protocol.PROXY_GOSSIP);
        out.writeUTF(d.proxyAddress.toString());
        out.writeInt(d.lastKnownState);        
    } 
        
    private void readProxy() throws IOException { 
        
        VirtualSocketAddress address = new VirtualSocketAddress(in.readUTF());
        int state = in.readInt();        

        knownProxies.addProxyDescription(address, state, peer.proxyAddress);
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
                
            default:
            
            }
                        
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
    
    public void activate() {
        // TODO: Use pool ?         
        new Thread(this).start();
    }
        
    public void run() { 

        while (!done) { 
            receive();
        }
    }
}
