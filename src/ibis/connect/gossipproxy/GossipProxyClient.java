package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

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
        
    private DirectSocketFactory factory;
    private SocketAddressSet localClientAddress; 
    
    private HashMap knownProxies = new HashMap();
    private List reachableProxies = new ArrayList();
    
    private class ProxyInfo { 
        final SocketAddressSet address; 
        boolean reachable; 
        boolean canReachMe;
        
        ProxyInfo(SocketAddressSet address, boolean reachable, boolean canReachMe) { 
            this.address = address;
            this.reachable = reachable;
            this.canReachMe = canReachMe;
        }
    }
        
    public GossipProxyClient(SocketAddressSet client) throws IOException {
                
        factory = DirectSocketFactory.getSocketFactory();
    
        this.localClientAddress = client;

        logger.info("Created GossipProxyClient for " + client);
    }
            
    public boolean [] addProxy(SocketAddressSet [] proxies) {

        boolean [] result = new boolean[proxies.length];
        
        for (int i=0;i<proxies.length;i++) { 
            if (proxies[i] != null) { 
                result[i] = addProxy(proxies[i]);
            } 
        } 
        
        return result;
    }
    
    public boolean addProxy(SocketAddressSet proxy) {

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
        
        DirectSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
        
        // Otherwise, try to set up a connection
        try { 
            s = factory.createSocket(proxy, DEFAULT_TIMEOUT, null);
            
            out = new DataOutputStream(s.getOutputStream());
                        
            out.write(ProxyProtocol.PROXY_CLIENT_REGISTER);
            out.writeUTF(localClientAddress.toString());
            out.flush();            
            
            in = new DataInputStream(s.getInputStream());
            int reply = in.readByte();
            
            switch (reply) {
            case ProxyProtocol.REPLY_CLIENT_REGISTRATION_ACCEPTED:
                logger.info("Proxy " + proxy + " accepted our registration.");
                break;
                
            case ProxyProtocol.REPLY_CLIENT_REGISTRATION_REFUSED:
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
            DirectSocketFactory.close(s, out, in);
        }
        
        synchronized (this) {
            // Could reach the machine, so update the proxy info
            info.reachable = true;
            reachableProxies.add(info);
        }
        
        return true;
    }

    private DirectSocket connectViaProxy(SocketAddressSet proxy, 
            SocketAddressSet target, int timeout) throws IOException { 
        
        DirectSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;
                
        boolean succes = false;
        
        try {
            s = factory.createSocket(proxy, timeout, null);
        
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());
                    
            out.write(ProxyProtocol.PROXY_CLIENT_CONNECT);            
            out.writeUTF(localClientAddress.toString());
            out.writeUTF(target.toString());
            out.writeInt(0);            
            
            out.flush();            
                
            int result = in.readByte();
            
            switch (result) { 
            case ProxyProtocol.REPLY_CLIENT_CONNECTION_ACCEPTED:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " accepted!");                
                succes = true;
                break;
            case ProxyProtocol.REPLY_CLIENT_CONNECTION_DENIED:
                logger.info("Connection to " + target + " via proxy " 
                        + proxy + " failed (connection denied)!");                                
                break;
            case ProxyProtocol.REPLY_CLIENT_CONNECTION_UNKNOWN_HOST:
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
                DirectSocketFactory.close(s, out, in);
                s = null;            
            }          
        } 
        
        return s;
    }
    
    public DirectSocket connect(SocketAddressSet target, int timeout) { 
        
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
    private void handleProxyTestConnect(DirectSocket s, DataInputStream in) 
        throws IOException { 
        
        SocketAddressSet proxy = new SocketAddressSet(in.readUTF()); 
        
        logger.info("Got connection test from " + proxy);
        
        

        
        
    }
    
    public void run() { 
        
        while (!done) { 
            try {
                 s = server.accept();                
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

        
        
        SocketAddressSet [] proxies = null;
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
        
        proxies = new SocketAddressSet[proxyCount];
        clients = new String[clientCount];
          
        clientCount = proxyCount = 0;
        
        for (int i=0;i<args.length;i++) {           
            if (args[i].equals("-c")) {
                clients[clientCount++] = args[++i];                
            }
            
            if (args[i].equals("-p")) {                
                try { 
                    proxies[proxyCount++] = new SocketAddressSet(args[++i]);
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

