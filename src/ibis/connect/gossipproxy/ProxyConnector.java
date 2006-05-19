package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;

class ProxyConnector extends Thread {
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ProxyConnector.class.getName());
    
    private static final int DEFAULT_TIMEOUT = 1000;
    private static final HashMap CONNECT_PROPERTIES = new HashMap();    
        
    private final ProxyList knownProxies;
    private final VirtualSocketFactory factory;    
    //private final VirtualSocketAddress local;
    private final String localAsString;
    
    private final LinkedList newProxies = new LinkedList();
    
    private boolean done = false;
    
    ProxyConnector(VirtualSocketFactory factory, String localAsString, 
            ProxyList knownProxies) {
        this.factory = factory;
        this.knownProxies = knownProxies;
        this.localAsString = localAsString;
        
        CONNECT_PROPERTIES.put("allowed.modules", "direct");        
    }
    
    synchronized void addNewProxy(ProxyDescription proxy) { 
        newProxies.addLast(proxy);
        notifyAll();
    }
    
    private synchronized ProxyDescription getNewProxy() {
        
        while (newProxies.size() == 0) { 
            try { 
                wait();
            } catch (InterruptedException e) {
                // ignore
            }
        }
        
        return (ProxyDescription) newProxies.removeFirst();
    }
         
    private boolean sendConnect(DataOutputStream out, DataInputStream in) 
        throws IOException { 

        logger.info("Sending connection request");
                
        out.write(Protocol.PROXY_CONNECT);
        out.writeUTF(localAsString);
        out.flush();

        int opcode = in.read();

        switch (opcode) {
        case Protocol.CONNECTION_ACCEPTED:
            logger.info("Connection request accepted");            
            return true;
        case Protocol.CONNECTION_DUPLICATE:
            logger.info("Connection request refused (duplicate)");
            return false;
        default:
            logger.warn("Got unknown reply from proxy!");
            return false;
        }
    }
    
    private void handleNewProxy() { 

        // Handles the connection setup to newly discoverd proxies. Note that 
        // there is a very nice race condition here, since the target proxy may
        // be doing exactly the same connection setup to us at this very moment.
        // As a result, we may get two half-backed connections between the 
        // proxies, because the state of the two receiving ends conflicts with 
        // the state of the sending parts....
        //        
        // To solve this problem, we must introduce some 'total order' on the 
        // proxies (i.e., by comparing the string form of their addresses) and 
        // let the smallest/largest one decide what to do...          
        ProxyDescription d = getNewProxy();
        
        if (d.haveConnection()) {
            // The connection was already created by the other side...
            return;
        }
            
        logger.info("Creating connection to " + d.proxyAddress);
        
        VirtualSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        boolean result = false;
        
        try { 
            s = factory.createClientSocket(d.proxyAddress, 
                    DEFAULT_TIMEOUT, CONNECT_PROPERTIES);
            
            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));
            
            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            int order = localAsString.compareTo(d.proxyAddress.toString());

            // If (order < 0) I must atomically grab the connection 'lock' 
            // before sending the request. It will return true if it is still 
            // free. If it isn't, we don't need to create the connection anymore 
            // and just send a ping message instead. If (order > 0) then we 
            // grab the lock after sending the connect message. 
            //
            // This approach ensures that if their are two machines trying to 
            // do a connection setup at the same time, the locks are always 
            // first grabbed on one of the two machines. This way one of the two 
            // will always win. Added bonus is that the receiving thread does 
            // not need to know anything about this.            
            // 
            // Note that we intentionally create the connection first, since
            // we don't want to grab the lock until we're absolutely sure that 
            // we're able to create the connection. If we wouldn't do this, we 
            // may 'accidently' block a connection from a machine that we are 
            // not able to connect to ourselves.
            // 

            if (order < 0) { 
                logger.info("I am master during connection setup");
                
                ProxyConnection c = new ProxyConnection(s, in, out, d);                
                result = d.createConnection(c);                
                
                if (!result) {
                    logger.info("Connection was already created!");
                    
                    // never mind...
                    out.write(Protocol.PROXY_PING);
                    out.writeUTF(localAsString);
                    out.flush();
                } else {
                    result = sendConnect(out, in);
                } 
            } else {   
                logger.info("I am slave during connection setup");
                
                result = sendConnect(out, in);

                if (result) {                 
                    ProxyConnection c = new ProxyConnection(s, in, out, d);                
                    result = d.createConnection(c);
                    
                    if (!result) { 
                        // This should not happen if the protocol works....
                        logger.warn("Race condition during connection setup!!");
                    }
                }                 
            }
        
        } catch (Exception e) {
            logger.warn("ProxyConnector got exception!", e);
        }
        
        if (!result) {
            logger.info("ProxyConnector failed to set up connection!");
            GossipProxy.close(s, in, out);
        }
    }
    
    public void run() {
    
        while (!done) {            
            handleNewProxy();
        }
    }    
}
