package ibis.connect.gossipproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;

import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

public class GossipProxy extends Thread {
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxyClient.class.getName());
        
    private static final int DEFAULT_PORT = 17878;
    
    private VirtualSocketFactory factory;
    private VirtualServerSocket server;
    private VirtualSocketAddress local;
    private String localAsString;
    
    private ProxyDescription localDescription;         
    private ProxyList proxies; 
    
    private ProxyConnector proxyConnector;
    
    private boolean done = false;
                
    public GossipProxy() throws IOException { 

        factory = VirtualSocketFactory.getSocketFactory();
        
        // TODO: Should I limit the address/modules that are used here ?? 
        server = factory.createServerSocket(DEFAULT_PORT, 50, null);
        local = server.getLocalSocketAddress();
        localAsString = local.toString();
        
        // Create a description for the local machine. 
        localDescription =  
            new ProxyDescription(server.getLocalSocketAddress(), null, 0, 0);
        
        localDescription.setReachable(1, 1, ProxyDescription.DIRECT);
        localDescription.setCanReachMe(1, 1);
        
        // Create the proxy list
        proxies = new ProxyList(localDescription);
        
        proxyConnector = new ProxyConnector(factory, localAsString, proxies);
    }
    
    ProxyDescription getProxyDescription(VirtualSocketAddress a) {
        return getProxyDescription(a, 0, null, false);        
    }
    
    ProxyDescription getProxyDescription(VirtualSocketAddress a, 
            boolean direct) {
        return getProxyDescription(a, 0, null, direct);        
    }
        
    synchronized ProxyDescription getProxyDescription(VirtualSocketAddress a, 
            int state, VirtualSocketAddress src, boolean direct) { 
        
        ProxyDescription tmp = proxies.get(a);
        
        if (tmp == null) { 
            tmp = new ProxyDescription(a, src, proxies.size()+1, state);
            proxies.add(tmp);                
            proxyConnector.addNewProxy(tmp);
        }
        
        if (direct && !tmp.canDirectlyReachMe()) { 
            tmp.setCanReachMe(proxies.size(), state);
        }        
        
        return tmp;
    }
    
    /*
    public synchronized void addClient(VirtualSocketAddress address) {         
        // TODO: Check if we can actually reach the client directly ? 
        localDescription.addClient(address);                
    }
    */
    
    private void activateConnection(ProxyConnection c) {
        // TODO: Should use threadpool
        new Thread(c).start();
    }
    
    private boolean handleIncomingProxyConnect(VirtualSocket s, 
            DataInputStream in, DataOutputStream out) throws IOException { 
    
        String otherAsString = in.readUTF();        
        VirtualSocketAddress proxy = new VirtualSocketAddress(otherAsString); 
                
        logger.info("Got connection from " + proxy);
        
        ProxyDescription d = getProxyDescription(proxy, true);
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
            activateConnection(c);               
            return true;
        } 
    }
    
    protected static void close(VirtualSocket s, InputStream in, OutputStream out) { 
        
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
                
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            s.close();
        } catch (Exception e) {
            // ignore
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
