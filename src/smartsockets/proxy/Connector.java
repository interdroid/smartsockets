package smartsockets.proxy;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.proxy.connections.Connections;
import smartsockets.proxy.connections.ProxyConnection;
import smartsockets.proxy.state.ProxyDescription;
import smartsockets.proxy.state.ProxyList;
import smartsockets.proxy.state.StateCounter;

class Connector extends CommunicationThread {
    
    private boolean done = false;
    
    Connector(StateCounter state, Connections connections,
            ProxyList knownProxies, DirectSocketFactory factory) {
        
        super("ProxyConnector", state, connections, knownProxies, factory);
    }

    private boolean sendConnect(DataOutputStream out, DataInputStream in) 
        throws IOException { 

        logger.info("Sending connection request");
                
        out.write(ProxyProtocol.CONNECT);
        out.writeUTF(localAsString);
        out.flush();

        int opcode = in.read();

        switch (opcode) {
        case ProxyProtocol.CONNECTION_ACCEPTED:
            logger.info("Connection request accepted");            
            return true;
        case ProxyProtocol.CONNECTION_REFUSED:
            logger.info("Connection request refused (duplicate)");
            return false;
        default:
            logger.warn("Got unknown reply from proxy!");
            return false;
        }
    }

    private void testConnection(ProxyDescription d) {

        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        
        // Creates a connection to a proxy to check if it is reachable. If so, 
        // it will send a ping. 
        logger.info("Creating test connection to " + d.proxyAddress);
                
        try { 
            s = factory.createSocket(d.proxyAddress, DEFAULT_TIMEOUT, null);
            
            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));
            
            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            out.write(ProxyProtocol.PING);
            out.writeUTF(localAsString);
            out.flush();            
            
            logger.info("Succesfully created connection!");                       
            d.setReachable();
           
        } catch (Exception e) {
            
            logger.info("Failed to set up connection!");
            d.setUnreachable();
            
        } finally {             
            DirectSocketFactory.close(s, out, in);
        } 
    }
    
    private void createConnection(ProxyDescription d) { 
                
        DirectSocket s = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        boolean result = false;
        ProxyConnection c = null;
        
        // Creates a connection to a newly discovered proxy. Note that there is 
        // a very nice race condition here, since the target proxy may be doing
        // exactly the same connection setup to us at this very moment.
        //
        // As a result, we may get two half-backed connections between the 
        // proxies, because the state of the two receiving ends conflicts with 
        // the state of the sending parts....
        //        
        // To solve this problem, we introduce some 'total order' on the proxies
        // by comparing the string form of their addresses. We then let the 
        // smallest one decide what to do...                                  
        boolean master = localAsString.compareTo(d.proxyAddress.toString()) < 0;
        
        logger.info("Creating connection to " + d.proxyAddress);
                
        try { 
            s = factory.createSocket(d.proxyAddress, 
                    DEFAULT_TIMEOUT, null);
            
            out = new DataOutputStream(
                    new BufferedOutputStream(s.getOutputStream()));
            
            in = new DataInputStream(
                    new BufferedInputStream(s.getInputStream()));

            // If I am the master I must atomically grab the connection 'lock' 
            // before sending the request. It will return true if it is still 
            // free. If it isn't, we don't need to create the connection anymore 
            // and just send a ping message instead. If I am the slave then we 
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
            // may 'accidently' block an incoming connection from a machine that
            // we are not able to connect to ourselves.        
            if (master) { 
                logger.info("I am master during connection setup");
                
                c = new ProxyConnection(s, in, out, d, connections, 
                        knownProxies, state);                                
                result = d.createConnection(c);                
                
                if (!result) {
                    logger.info("Connection was already created!");
                    
                    // never mind...
                    out.write(ProxyProtocol.PING);
                    out.writeUTF(localAsString);
                    out.flush();
                } else {
                    result = sendConnect(out, in);                    
                } 
            } else {   
                logger.info("I am slave during connection setup");
                
                result = sendConnect(out, in);

                if (result) {                 
                    c = new ProxyConnection(s, in, out, d, connections, 
                            knownProxies, state);                
                    result = d.createConnection(c);
                    
                    if (!result) { 
                        // This should not happen if the protocol works....
                        logger.warn("Race condition triggered during " +
                                "connection setup!!");
                    }
                }                 
            }
        
            d.setReachable();       
        } catch (Exception e) {
            logger.warn("Got exception!", e);        
            d.setUnreachable();
       }
        
        if (result) {
            logger.info("Succesfully created connection!");
            connections.addConnection(d.proxyAddress.toString(), c);
            c.activate();
        } else { 
            logger.info("Failed to set up connection!");
            DirectSocketFactory.close(s, out, in);
        }
    }
    
    private void handleNewProxy() { 

        // Handles the connection setup to newly discovered proxies.
        ProxyDescription d = knownProxies.nextProxyToCheck();
        
        if (d.haveConnection()) {
            // The connection was already created by the other side. Create a 
            // test connection to see if the proxy is reachable from here. 
            testConnection(d);
        } else {       
            createConnection(d);
        } 
        
        knownProxies.putBack(d);
    } 
   
    public void run() {
    
        while (!done) {            
            handleNewProxy();
        }
    }    
}
