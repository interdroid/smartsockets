package ibis.connect.gossipproxy;


import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

public class GossipProxyClient {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(GossipProxyClient.class.getName());
    
    private static final int DEFAULT_TIMEOUT = 1000;
    private static final HashMap CONNECT_PROPERTIES = new HashMap();    
    
    private VirtualSocketFactory factory;
    //private VirtualServerSocket server;
    
    private VirtualSocketAddress localClientAddress; 
    
    private HashMap knownProxies = new HashMap();
    private List reachableProxies = new ArrayList();
    
    private class ProxyInfo { 
        final VirtualSocketAddress address; 
        boolean reachable; 
        boolean canReachMe;
        
        ProxyInfo(VirtualSocketAddress address, boolean reachable, boolean canReachMe) { 
            this.address = address;
            this.reachable = reachable;
            this.canReachMe = canReachMe;
        }
    }
        
    public GossipProxyClient(VirtualSocketAddress client) throws IOException {
                
        CONNECT_PROPERTIES.put("allowed.modules", "direct");
        
        factory = VirtualSocketFactory.getSocketFactory();
    
        this.localClientAddress = client;

        logger.info("Created GossipProxyClient for " + client);
    }
            
    public boolean [] addProxy(VirtualSocketAddress [] proxies) {

        boolean [] result = new boolean[proxies.length];
        
        for (int i=0;i<proxies.length;i++) { 
            if (proxies[i] != null) { 
                result[i] = addProxy(proxies[i]);
            } 
        } 
        
        return result;
    }
    
    public boolean addProxy(VirtualSocketAddress proxy) {

        logger.info("Adding proxy " + proxy);
        
        // Check if the proxy is already known
        if (knownProxies.containsKey(proxy)) {
            logger.info("Proxy " + proxy + " already known!");
            return true;
        }

        ProxyInfo info = new ProxyInfo(proxy, false, false);
        
        // Add the candidate to the list known proxies
        synchronized (this) {         
            knownProxies.put(proxy, info);
        } 
        
        VirtualSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
        
        // Otherwise, try to set up a connection
        try { 
            s = factory.createClientSocket(proxy, DEFAULT_TIMEOUT,
                    CONNECT_PROPERTIES);
            
            out = new DataOutputStream(s.getOutputStream());
                        
            out.write(Protocol.PROXY_CLIENT_REGISTER);
            out.writeUTF(localClientAddress.toString());
            out.flush();            
            
            in = new DataInputStream(s.getInputStream());
            int reply = in.readByte();
            
            switch (reply) {
            case Protocol.REPLY_CLIENT_REGISTRATION_ACCEPTED:
                logger.info("Proxy " + proxy + " accepted our registration.");
                break;
                
            case Protocol.REPLY_CLIENT_REGISTRATION_REFUSED:
                logger.info("Proxy " + proxy + " refused our registration.");
                return false;                 
                                
            default:
                logger.info("Proxy " + proxy + " returned gibberish!");
                return false;                             
            }

        } catch (IOException e) {           
            logger.warn("Could not contact Proxy " + proxy, e);
            return false;
        } finally { 
            VirtualSocketFactory.close(s, out, in);
        }
        
        synchronized (this) {
            // Could reach the machine, so update the proxy info
            info.reachable = true;
            reachableProxies.add(info);
        }
        
        return true;
    }

    private VirtualSocket connectViaProxy(VirtualSocketAddress proxy, 
            VirtualSocketAddress target, int timeout) throws IOException { 
        
        VirtualSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
                
        boolean succes = false;
        
        try {
            s = factory.createClientSocket(proxy, timeout,
                    CONNECT_PROPERTIES);
        
            out = new DataOutputStream(s.getOutputStream());
                    
            out.write(Protocol.PROXY_CLIENT_CONNECT);
            out.writeUTF(target.toString());
            out.flush();            
    
            in = new DataInputStream(s.getInputStream());
            
            int result = in.readByte();
            
            switch (result) { 
            case Protocol.REPLY_CLIENT_CONNECTION_ACCEPTED:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " accepted!");                
                succes = true;
                break;
            case Protocol.REPLY_CLIENT_CONNECTION_DENIED:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " failed (connection denied)!");                                
                break;
            case Protocol.REPLY_CLIENT_CONNECTION_UNKNOWN_HOST:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " failed (unknown host)!");                                                
                break;
            default:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " failed (unknown reply)!");                                                                                
            }
        } catch (Exception e) {
            logger.info("Connection to " + target + " via proxy " + proxy 
                    + " failed (got exception " + e.getMessage() + ")!");            
        } finally {                      
            if (!succes) { 
                VirtualSocketFactory.close(s, out, in);
                s = null;            
            }          
        } 
        
        return s;
    }
    
    public VirtualSocket connect(VirtualSocketAddress target, int timeout) { 
        
        Iterator itt = reachableProxies.iterator();
        
        while (itt.hasNext()) { 
            
            ProxyInfo proxy = (ProxyInfo) itt.next();
            
            try { 
                return connectViaProxy(proxy.address, target, timeout);
            } catch (IOException e) {
                // TODO: store and return later ? 
                // ignore for now.......
            }            
        }    
        
        // TODO: Exception ? 
        return null;
    }
    
    /*
    private void handleProxyTestConnect(VirtualSocket s, DataInputStream in) 
        throws IOException { 
        
        VirtualSocketAddress proxy = new VirtualSocketAddress(in.readUTF()); 
        
        logger.info("Got connection test from " + proxy);
        
        

        
        
    }
    
    public void run() { 
        
        while (!done) { 
            try {
                VirtualSocket s = server.accept();                
                DataInputStream in = new DataInputStream(s.getInputStream());
                
                int opcode = in.read();
                
                switch (opcode) {
                case Protocol.PROXY_TEST_CONNECT:
                    handleProxyTestConnect(s, in);
                    
                    break;
                default:
                    break;
                }
                   
                
                
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
           
        
        
    }
    
    public static void main(String [] args) { 

        
        
        VirtualSocketAddress [] proxies = null;
        String [] clients = null;

        int proxyCount = 0; 
        int clientCount = 0;        
        
        for (int i=0;i<args.length;i++) {           
            if (args[i].equals("-c")) {
                clientCount++;            
            }
            
            if (args[i].equals("-p")) {
                proxyCount++;
            }
        }         
        
        proxies = new VirtualSocketAddress[proxyCount];
        clients = new String[clientCount];
          
        clientCount = proxyCount = 0;
        
        for (int i=0;i<args.length;i++) {           
            if (args[i].equals("-c")) {
                clients[clientCount++] = args[++i];                
            }
            
            if (args[i].equals("-p")) {                
                try { 
                    proxies[proxyCount++] = new VirtualSocketAddress(args[++i]);
                } catch (Exception e) {
                    logger.warn("Skipping proxy address: " + args[i], e);              
                }
            }
            
            if (args[i].equals("-i"))  { 
                interactive = true;
            }
        } 
        
        try {
            new GossipProxyClient(proxies);
        } catch (IOException e) {
            logger.warn("Oops: ", e);
        }        
    }
    */
}

