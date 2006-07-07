/**
 * 
 */
package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.util.LinkedList;

import org.apache.log4j.Logger;

class ConnectionSetup implements Runnable {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ConnectionSetup.class.getName());
    
    private VirtualSocketFactory factory; 
    private ClientConnections connections; 
    private Connection c; 
    private LinkedList proxies; 
        
    ConnectionSetup(VirtualSocketFactory factory, ClientConnections connections,
            Connection c, LinkedList proxies) {
        
        this.factory = factory;
        this.connections = connections;        
        this.c = c;
        this.proxies = proxies;
    }

    private boolean connectViaProxy(Connection c, ProxyDescription p) {                
        // TODO implement!
        return false;
    }

    private boolean connectToClient(Connection c) {
        
        try {
            logger.info("Attempting to connect to " + c.targetAsString);
            
            VirtualSocketAddress target = new VirtualSocketAddress(c.targetAsString);
            
            // Create the connection
            c.socketB = factory.createClientSocket(target, 10000, null);
            c.outB = c.socketB.getOutputStream();            
            c.inB = c.socketB.getInputStream();
            
            // Create the forwarders and start them
            c.forwarder1 = new Forwarder(c.inA, c.outB, connections, c.id);
            c.forwarder2 = new Forwarder(c.inB, c.outA, connections, c.id);
            
            c.forwarder1.start();
            c.forwarder2.start();
            
            logger.info("Connection to " + c.targetAsString + " created!");
                        
            return true;
        
        } catch (Exception e) {
            logger.info("Connection setup to " + c.targetAsString + " failed", e);            
            VirtualSocketFactory.close(c.socketB, c.outB, c.inB);            
        }
        
        return false;
    }
    
    public void run() {

        // The client part of c should already be filled. This thread will 
        // attempt to fill the target part using the proxy list.
                    
        // This should be in a seperate thread, since it may take a while!        
        for (int i=0;i<proxies.size();i++) { 
            ProxyDescription p = (ProxyDescription) proxies.removeFirst();
            
            if (p.isLocal()) { 
                if (connectToClient(c)) { 
                    ClientConnections.logger.info("Succesfully created connection to " 
                            + c.targetAsString);
                    return;                        
                }
            } else {
                if (connectViaProxy(c, p)) { 
                    ClientConnections.logger.info("Succesfully created connection to " 
                            + c.targetAsString);
                    return;                        
                }
            }
        }
     
        ClientConnections.logger.info("Failed to create connection to " + c.targetAsString);            
        
        try { 
            // Failed to connect, so give up....
            c.outA.write(Protocol.REPLY_CLIENT_CONNECTION_DENIED);
            c.outA.flush();
        } catch (Exception e) {
            ClientConnections.logger.error("Failed to send reply to client!", e);
        } finally { 
            VirtualSocketFactory.close(c.socketA, c.outA, c.inA);
        }

        
        
        
    } 
}