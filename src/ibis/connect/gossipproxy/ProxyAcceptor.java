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
import java.util.LinkedList;

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
        d.setCanReachMe();
        
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
   
    private boolean handleClientConnect(VirtualSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {
        
        String clientAsString = in.readUTF();
        String targetAsString = in.readUTF();
                        
        logger.info("Got request client " + clientAsString 
                + " to connect to " + targetAsString);
        
        // Add client to list of clients we know...
        ProxyDescription tmp = knownProxies.getLocalDescription();
        tmp.addClient(clientAsString);
        
        // First check if we known the target ourselves:
        if (tmp.clients.contains(targetAsString)) {
            logger.info("We know target " + targetAsString + " ourselves!");            
            
            if (connectToClient(s, out, in)) { 
                return true;
            }
        }
                
        // Next, see if we known any proxies that know the target machine...
        LinkedList proxies = knownProxies.findClient(targetAsString, false);
        
        if (proxies.size() == 0) {
            // Nobody knows the target, so we give up for now...
            logger.info("Nobody seems to know " + targetAsString);            
            out.writeByte(Protocol.REPLY_CLIENT_CONNECTION_UNKNOWN_HOST);
            out.flush();
            return false;                
        }

        logger.info("Found " + proxies.size() + " proxies that know " 
                + targetAsString);            
        
        for (int i=0;i<proxies.size();i++) { 
            ProxyDescription p = (ProxyDescription) proxies.removeFirst();          
            logger.info("  trying proxy " + i + " -> " + p.proxyAddress);            
            
            if (connectViaProxy(s, out, in)) { 
                return true;
            }
        }
        
        // Failed to connect, so give up....
        out.writeByte(Protocol.REPLY_CLIENT_CONNECTION_DENIED);
        out.flush();                
        return false;
    }

    private boolean connectViaProxy(VirtualSocket s, DataOutputStream out, DataInputStream in) {
        // TODO Auto-generated method stub
        return false;
    }

    private boolean connectToClient(VirtualSocket s, DataOutputStream out, DataInputStream in) {
        // TODO Auto-generated method stub
        return false;
    }

    private boolean handleClientRegistration(VirtualSocket s, DataInputStream in, DataOutputStream out) throws IOException {

        // Read the clients address. 
        String clientAsString = in.readUTF();        
        // VirtualSocketAddress client = new VirtualSocketAddress(clientAsString); 
                        
        logger.info("Got connection from client: " + clientAsString);
        
        ProxyDescription tmp = knownProxies.getLocalDescription();
        tmp.addClient(clientAsString);

        // Always accept the connection for now.              
        out.writeByte(Protocol.REPLY_CLIENT_REGISTRATION_ACCEPTED);
        out.flush();       

        // TODO: should check here is we can reach the client. If so then 
        // all is well, if not, then we should refuse the connection or keep it 
        // open (which doesn't really scale) ...  
        
        // Always return false, so the main thread will close the connection. 
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
        
            case Protocol.PROXY_CLIENT_REGISTER:
                result = handleClientRegistration(s, in, out);
                break;
                
            case Protocol.PROXY_CLIENT_CONNECT:
                result = handleClientConnect(s, in, out);
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
