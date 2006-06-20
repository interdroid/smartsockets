package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class ProxyConnector extends CommunicationThread {
    
    private boolean done = false;
    
    ProxyConnector(ProxyList knownProxies, VirtualSocketFactory factory) {        
        super(knownProxies, factory);
    }

    private boolean sendConnect(DataOutputStream out, DataInputStream in) 
        throws IOException { 

        logger.info("Sending connection request");
                
        out.write(Protocol.PROXY_CONNECT);
        out.writeUTF(localAsString);
        out.flush();

        int opcode = in.read();

        switch (opcode) {
        case Protocol.REPLY_CONNECTION_ACCEPTED:
            logger.info("Connection request accepted");            
            return true;
        case Protocol.REPLY_CONNECTION_REFUSED:
            logger.info("Connection request refused (duplicate)");
            return false;
        default:
            logger.warn("Got unknown reply from proxy!");
            return false;
        }
    }
              
    private void createConnection(ProxyDescription d) { 
                
        VirtualSocket s = null;
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
            s = factory.createClientSocket(d.proxyAddress, 
                    DEFAULT_TIMEOUT, CONNECT_PROPERTIES);
            
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
                
                c = new ProxyConnection(s, in, out, d, knownProxies);                
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
                    c = new ProxyConnection(s, in, out, d, knownProxies);                
                    result = d.createConnection(c);
                    
                    if (!result) { 
                        // This should not happen if the protocol works....
                        logger.warn("Race condition triggered during " +
                                "connection setup!!");
                    }
                }                 
            }
        
            knownProxies.isReachable(d);            
        } catch (Exception e) {
            logger.warn("Got exception!", e);
            knownProxies.isUnreachable(d);
        }
        
        if (result) {
            logger.info("Succesfully created connection!");
            c.activate();
        } else { 
            logger.info("Failed to set up connection!");
            close(s, in, out);
        }
    }
    
    private void handleNewProxy() { 

        // Handles the connection setup to newly discovered proxies.
        ProxyDescription d = knownProxies.getUnconnectedProxy();
        
        if (d.haveConnection()) {
            // The connection was already created by the other side...
            return;
        }
      
        createConnection(d);
    } 
   
    public void run() {
    
        while (!done) {            
            handleNewProxy();
        }
    }    
}
