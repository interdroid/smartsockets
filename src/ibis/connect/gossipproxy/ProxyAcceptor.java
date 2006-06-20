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
    
    ProxyAcceptor(StateCounter state, ProxyList knownProxies, 
            VirtualSocketFactory factory) throws IOException {
        
        super("ProxyAcceptor", state, knownProxies, factory);
        server = factory.createServerSocket(DEFAULT_PORT, 50, null);        
        setLocal(server.getLocalSocketAddress());        
    }
        
    private boolean handleIncomingProxyConnect(VirtualSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 
    
        String otherAsString = in.readUTF();        
        VirtualSocketAddress addr = new VirtualSocketAddress(otherAsString); 
                
        logger.info("Got connection from " + addr);
        
        ProxyDescription d = knownProxies.add(addr);        
        d.setCanReachMe(state);
        
        ProxyConnection c = 
            new ProxyConnection(s, in, out, d, knownProxies, state);
        
        if (!d.createConnection(c)) { 
            // There already was a connection with this proxy...            
            logger.info("Connection from " + addr + " refused (duplicate)");
            
            out.write(Protocol.REPLY_CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                         
            // We just created a connection to this proxy.
            logger.info("Connection from " + addr + " accepted");
            
            out.write(Protocol.REPLY_CONNECTION_ACCEPTED);            
            out.flush();
            
            // Now activate it. 
            c.activate();
            return true;
        }     
    }
    
    private boolean handlePing(VirtualSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException {
     
        String sender = in.readUTF();         
        logger.info("Got ping from: " + sender);      
        return false;
    }    

    private void doAccept() {
        
        VirtualSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        boolean result = false;
        
        logger.info("Doing accept.");
        
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
                
            case Protocol.PROXY_PING:
                result = handlePing(s, in, out);                   
                break;
        
            default:
                break;
            }
        } catch (Exception e) {
            logger.warn("Failed to accept connection!", e);
            result = false;
        }
        
        if (!result) { 
            close(s, in, out);
        }
   
    }
    
    public void run() { 
        
        while (!done) {           
            doAccept();            
        }
    }       
}
