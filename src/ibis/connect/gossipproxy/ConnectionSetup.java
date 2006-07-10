/**
 * 
 */
package ibis.connect.gossipproxy;

import ibis.connect.util.Forwarder;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;

class ConnectionSetup implements Runnable {

    protected static final HashMap CONNECT_PROPERTIES = new HashMap();    
    
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ConnectionSetup.class.getName());
    
    private VirtualSocketFactory factory; 
    private ClientConnections connections; 
    private Connection c; 
    
    private LinkedList proxies; 
    private LinkedList skipProxies;        
    
    ConnectionSetup(VirtualSocketFactory factory, ClientConnections connections,
            Connection c, LinkedList proxies, LinkedList skipProxies) {
        
        this.factory = factory;
        this.connections = connections;        
        this.c = c;
        this.proxies = proxies;
        this.skipProxies = skipProxies;
        
        CONNECT_PROPERTIES.put("connect.module.type.allow", "direct");           
    }

    private boolean connectViaProxy(Connection c, ProxyDescription p) {                

        try {
            logger.info("Attempting to connect to client " + c.targetAsString);
            
            VirtualSocketAddress target = new VirtualSocketAddress(c.targetAsString);
            
            // Create the connection
            c.socketB = factory.createClientSocket(target, 10000, 
                    CONNECT_PROPERTIES);
            
            c.outB = c.socketB.getOutputStream();            
            c.inB = c.socketB.getInputStream();

            logger.info("Connection " + c.id + " created!");
                        
            c.outA.write(Protocol.REPLY_CLIENT_CONNECTION_ACCEPTED);
            c.outA.flush();
                                   
            String label1 = "[" + c.number + ": " + c.clientAsString + " --> "
                + c.targetAsString + "]";
            
            String label2 = "[" + c.number + ": " + c.clientAsString + " <-- "
                + c.targetAsString + "]";
                    
            // Create the forwarders and start them
            c.forwarder1 = new Forwarder(c.inA, c.outB, connections, c.id, label1);
            c.forwarder2 = new Forwarder(c.inB, c.outA, connections, c.id, label2);
            
            connections.addConnection(c.id, c);
            
            c.forwarder1.start();
            c.forwarder2.start();
            
            logger.info("Connection forwarders started!");
            
            return true;
        
        } catch (Exception e) {
            logger.info("Connection setup to " + c.targetAsString + " failed", e);            
            VirtualSocketFactory.close(c.socketB, c.outB, c.inB);            
        }
        
        return false;
    }

    private boolean connectToClient(Connection c) {
        
        try {
            logger.info("Attempting to connect to client " + c.targetAsString);
            
            VirtualSocketAddress target = new VirtualSocketAddress(c.targetAsString);
            
            // Create the connection
            c.socketB = factory.createClientSocket(target, 10000, 
                    CONNECT_PROPERTIES);
            
            c.outB = c.socketB.getOutputStream();            
            c.inB = c.socketB.getInputStream();

            logger.info("Connection " + c.id + " created!");
                        
            c.outA.write(Protocol.REPLY_CLIENT_CONNECTION_ACCEPTED);
            c.outA.flush();
                                   
            String label1 = "[" + c.number + ": " + c.clientAsString + " --> "
                + c.targetAsString + "]";
            
            String label2 = "[" + c.number + ": " + c.clientAsString + " <-- "
                + c.targetAsString + "]";
                    
            // Create the forwarders and start them
            c.forwarder1 = new Forwarder(c.inA, c.outB, connections, c.id, label1);
            c.forwarder2 = new Forwarder(c.inB, c.outA, connections, c.id, label2);
            
            connections.addConnection(c.id, c);
            
            c.forwarder1.start();
            c.forwarder2.start();
            
            logger.info("Connection forwarders started!");
            
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
                    logger.info("Succesfully created connection to " 
                            + c.targetAsString);
                    // Succesfully connected to client!                 
                    return;                        
                }
            } else {
                if (connectViaProxy(c, p)) { 
                    logger.info("Succesfully created connection to " 
                            + c.targetAsString);
                    return;                        
                }
            }
        }
     
        logger.info("Failed to create connection to " + c.targetAsString);            
        
        try { 
            // Failed to connect, so give up....
            c.outA.write(Protocol.REPLY_CLIENT_CONNECTION_DENIED);
            c.outA.flush();
        } catch (Exception e) {
            logger.error("Failed to send reply to client!", e);
        } finally { 
            VirtualSocketFactory.close(c.socketA, c.outA, c.inA);
        }
    } 
}