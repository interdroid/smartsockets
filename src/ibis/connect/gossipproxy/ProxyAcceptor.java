package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectServerSocket;
import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.gossipproxy.servicelink.ServiceLinkHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public class ProxyAcceptor extends CommunicationThread {
    
    private DirectServerSocket server;
    private boolean done = false;
    
    private ClientConnections connections; 
    private int number = 0;
    
    private ServiceLinkHandler serviceLinkHandler;
    
    ProxyAcceptor(StateCounter state, ProxyList knownProxies, 
            DirectSocketFactory factory, ClientConnections connections) 
            throws IOException {
        
        super("ProxyAcceptor", state, knownProxies, factory);        
        this.connections = connections;
        
        server = factory.createServerSocket(DEFAULT_PORT, 50, null);        
        setLocal(server.getAddressSet());        
        
        serviceLinkHandler = new ServiceLinkHandler(knownProxies);         
    }
    
    private boolean handleIncomingProxyConnect(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 
        
        String otherAsString = in.readUTF();        
        SocketAddressSet addr = new SocketAddressSet(otherAsString); 
        
        logger.info("Got connection from " + addr);
        
        ProxyDescription d = knownProxies.add(addr);        
        d.setCanReachMe();
        
        ProxyConnection c = 
            new ProxyConnection(s, in, out, d, knownProxies);
        
        if (!d.createConnection(c)) { 
            // There already was a connection with this proxy...            
            logger.info("Connection from " + addr + " refused (duplicate)");
            
            out.write(ProxyProtocol.REPLY_CONNECTION_REFUSED);
            out.flush();
            return false;
        } else {                         
            // We just created a connection to this proxy.
            logger.info("Connection from " + addr + " accepted");
            
            out.write(ProxyProtocol.REPLY_CONNECTION_ACCEPTED);            
            out.flush();
            
            // Now activate it. 
            c.activate();
            return true;
        }     
    }
    
    private boolean handlePing(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException {
        
        String sender = in.readUTF();         
        logger.info("Got ping from: " + sender);      
        return false;
    }    
    
    private boolean handleClientConnect(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {
        
        LinkedList skipProxies = new LinkedList();
        
        String clientAsString = in.readUTF();
        String targetAsString = in.readUTF();
        
        int skipProxiesCount = in.readInt();
        
        if (skipProxiesCount > 0) {
            skipProxies.add(in.readUTF());
        } 
        
        logger.info("Got request to connect " + clientAsString 
                + " to " + targetAsString);
        
        // See if we known any proxies that know the target machine. Note that 
        // if the local proxy is able to connect to the client, it will be
        // returned at the head of the list (so we try it first).    
        LinkedList proxies = knownProxies.findClient(targetAsString, skipProxies);
        
        if (proxies.size() == 0) {
            
            if (skipProxies.size() == 0) { 
                // Nobody knows the target, so we give up for now...
                logger.info("Nobody seems to know " + targetAsString);            
            } else { 
                // All proxies have been tried already, so we give up...
                logger.info("All proxies have been tried " + targetAsString);                            
            }
            
            out.writeByte(ProxyProtocol.REPLY_CLIENT_CONNECTION_UNKNOWN_HOST);
            out.flush();
            return false;                
        }
        
        logger.info("Found " + proxies.size() + " proxies that know " 
                + targetAsString);            
        
        Connection c = new Connection(clientAsString, targetAsString, number, 
                s, in, out);
        
        ConnectionSetup cs = new ConnectionSetup(factory, connections, c, 
                proxies, skipProxies);
        
        logger.info("Starting thread to create " + c);
        
        // TODO threadpool ? 
        new Thread(cs, "ConnectionSetup: " + c.id).start();                
        
        return true;
    }
    
    private boolean handleClientRegistration(DirectSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException {
        
        // Read the clients address. 
        String clientAsString = in.readUTF();        
        
        logger.info("Got connection from client: " + clientAsString);
        
        ProxyDescription tmp = knownProxies.getLocalDescription();
        tmp.addClient(clientAsString);
        
        // Always accept the connection for now.              
        out.writeByte(ProxyProtocol.REPLY_CLIENT_REGISTRATION_ACCEPTED);
        out.flush();       
        
        // TODO: should check here is we can reach the client. If so then 
        // all is well, if not, then we should refuse the connection or keep it 
        // open (which doesn't really scale) ...  
        
        // Always return false, so the main thread will close the connection. 
        return false;
    }
    
    private void doAccept() {
        
        DirectSocket s = null;
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
            case ProxyProtocol.PROXY_CONNECT:
                result = handleIncomingProxyConnect(s, in, out);                   
                break;
                
            case ProxyProtocol.PROXY_PING:
                result = handlePing(s, in, out);                   
                break;
                
                // TODO: remove!                
            case ProxyProtocol.PROXY_CLIENT_REGISTER:
                result = handleClientRegistration(s, in, out);
                break;
                
            case ProxyProtocol.PROXY_CLIENT_CONNECT:
                result = handleClientConnect(s, in, out);
                break;
                
            case ProxyProtocol.PROXY_SERVICELINK_CONNECT:
                result = serviceLinkHandler.handleConnection(s, in, out);
                break;                
                
            default:
                break;
            }
        } catch (Exception e) {
            logger.warn("Failed to accept connection!", e);
            result = false;
        }
        
        if (!result) { 
            DirectSocketFactory.close(s, out, in);
        }   
    }
    
    public void run() { 
        
        while (!done) {           
            doAccept();            
        }
    }       
}
