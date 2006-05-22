package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ProxyAcceptor extends CommunicationThread {
        
    private VirtualServerSocket server;
    private boolean done = false;
    
    ProxyAcceptor(GossipProxy parent, ProxyList knownProxies, 
            VirtualSocketFactory factory) throws IOException {        
        super(parent, knownProxies, factory);
        server = factory.createServerSocket(DEFAULT_PORT, 50, null);        
        setLocal(server.getLocalSocketAddress());        
    }
        
    private boolean handleIncomingProxyConnect(VirtualSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 
    
        String otherAsString = in.readUTF();        
        VirtualSocketAddress addr = new VirtualSocketAddress(otherAsString); 
                
        logger.info("Got connection from " + addr);
        
        ProxyDescription d = parent.getProxyDescription(addr, true);
        ProxyConnection c = new ProxyConnection(s, in, out, d);
            
        if (!d.createConnection(c)) { 
            // There already was a connection with this proxy... 
            out.write(Protocol.CONNECTION_DUPLICATE);
            out.flush();
            return false;
        } else {                         
            // We just created a connection to this proxy. 
            out.write(Protocol.CONNECTION_ACCEPTED);            
            out.flush();
            
            // Now activate it. 
            parent.activateConnection(c);               
            return true;
        } 
    }
    
    public void run() { 
        
        while (!done) {
            
            VirtualSocket s = null;
            DataInputStream in = null;
            DataOutputStream out = null;
            boolean result = false;
            
            try {
                s = server.accept();                
                in = new DataInputStream(
                        new BufferedInputStream(s.getInputStream()));
                
                out = new DataOutputStream(
                        new BufferedOutputStream(s.getOutputStream()));
                
                int opcode = in.read();
                
                switch (opcode) {
                case Protocol.PROXY_CONNECT:
                    result = handleIncomingProxyConnect(s, in, out);                   
                    break;
                default:
                    break;
                }
            } catch (Exception e) {
                logger.warn("GossipProxy failed to accept connection!", e);
                result = false;
            }
            
            if (!result) { 
                close(s, in, out);
            }
        }
    }       
}
