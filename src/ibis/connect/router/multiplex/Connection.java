/**
 * 
 */
package ibis.connect.router.multiplex;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.proxy.servicelink.Client;
import ibis.connect.util.Forwarder;
import ibis.connect.util.ForwarderDoneCallback;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class Connection implements Runnable, Protocol, ForwarderDoneCallback {       

    private final Router parent;
    
    private VirtualSocket socketToClient;
    private DataOutputStream outToClient;
    private DataInputStream inFromClient;    
    
    private VirtualSocket socketToTarget;
    private DataOutputStream outToTarget;
    private DataInputStream inFromTarget;
    
    private String label1;    
    private String label2;    
    
    private int forwarderDone = 0;
   
    Connection(VirtualSocket sc, Router parent) throws IOException {
        
        Router.logger.debug("Created new connection to " + sc);
                
        this.socketToClient = sc;
        this.parent = parent;
        
        // No extra buffer here, since we will mostly send byte[]
        outToClient = new DataOutputStream(sc.getOutputStream());
        inFromClient = new DataInputStream(sc.getInputStream());
    }
    
    private boolean connect(VirtualSocketAddress target, long timeout) {  
    
        try { 
            socketToTarget = parent.connect(target, timeout);
            outToTarget = new DataOutputStream(socketToTarget.getOutputStream());
            inFromTarget = new DataInputStream(socketToTarget.getInputStream());        
            return true; 
        } catch (IOException e) {
            Router.logger.debug("Failed to create connection to " + target);
            VirtualSocketFactory.close(socketToTarget, outToTarget, inFromTarget);
            return false;
        }
    }
       
    private boolean connectViaRouter(Client router, VirtualSocketAddress target,
            long timeout) {

        // Connects to a client via another router. This simply comes down to 
        // connecting to the router, and then acting as is we are a client 
        // ourselves        
        boolean succes = false;
        
        try {
            Router.logger.debug("Connecting to router: " + router);
                        
            VirtualSocketAddress r = router.getService("router");            
           
            if (connect(r, timeout)) {
                Router.logger.debug("Connection to router created!!");
                Router.logger.debug("Forwarding target: " + target.toString());
                                
                outToTarget.writeUTF(target.toString());                
                outToTarget.writeLong(timeout);
                outToTarget.flush();

                Router.logger.debug("Waiting for router reply...");
                        
                // TODO set timeout!!!
                int result = inFromTarget.readByte();
                
                switch (result) {
                case REPLY_OK:
                    Router.logger.debug("Connection setup succesfull!");            
                    succes = true;
                    break;

                case REPLY_FAILED:
                    Router.logger.debug("Connection setup failed!");
                    break;
                
                default:
                    Router.logger.debug("Connection setup returned junk!");
                }
            }            
        } catch (IOException e) {
            Router.logger.debug("Connection setup resulted in exception!", e);
        }
            
        if (!succes) { 
            VirtualSocketFactory.close(socketToTarget, outToTarget, inFromTarget);
        }
        
        return succes;
    }

    
    private boolean connectToTarget(VirtualSocketAddress target, long timeout, 
            SocketAddressSet [] directions) { 
                        
        // Check if the target is local to my proxy. If so, my local proxy 
        // address should be in the first entry of the directions array.
        SocketAddressSet proxy = parent.getProxyAddress();

        if (proxy == null) { 
            Router.logger.debug("Cannot forward connection: failed to get " +
                    "local proxy address!");
            return false;
        }
        
        int startIndex = 0;
        
        if (directions[0].equals(proxy)) {
             
            // The client should be local to this router, so we can connect to 
            // it directly.             
            Router.logger.debug("Client is local to my proxy!!");
                        
            if (connect(target, timeout)) { 
                Router.logger.debug("Created local connection to client " 
                        + target);
                return true;
            } else {                
                // We are not allowed to continue here... It doesn't make sense
                // to try other proxies if the client is supposed to be local to
                // ours.            
                return false;
            }
            
        } else {            
            Router.logger.debug("Client is NOT local to my proxy!!");
            Router.logger.debug("I am         : " + proxy);
            Router.logger.debug("First hop is : " + directions[0]);
        }
          
        Router.logger.debug("Directions to client machine: "); 

        for (int i=0;i<directions.length;i++) {
            Router.logger.debug(" " + i + " : " + directions[i]);
        }
        
        // The target wasn't local. We now traverse each of the proxies one by
        // one, and check if we can find any router processes associated with 
        // them. If ther are, we try to connect to the target via one of these
        // routers...        
        for (int i=startIndex;i<directions.length;i++) {
            
            Client [] result = null;
            
            try { 
                // Get all the routers associated with this proxy
                result = parent.findClients(directions[i], "router");
            } catch (Exception e) {
                Router.logger.debug("Failed to contact proxy!", e);                                
            }

            if (result == null || result.length == 0) { 
                Router.logger.debug("Proxy " + directions[i] + " does not "
                        + "know any routers!");                    
            } else { 
                Router.logger.debug("Proxy " + directions[i] + " knows the"
                        + " following routers:");
                    
                for (int x=0;x<result.length;x++) { 
                    Router.logger.debug(" " + i + " - " + result[x]);                            
                }
                
                // Try to connect to the clients using one of the routers
                for (int x=0;x<result.length;x++) {
                    if (connectViaRouter(result[i], target, timeout)) { 
                        return true;                        
                    }
                } 
            } 
        }
        
        return false;
    }
       
    private boolean connect() throws IOException { 
         
        // This method reads the target address from the client, asks for  
        // directions from the proxy, and (if directions are available) 
        // attempts to create a connection to the target.          
        boolean succes = false;
        
        Router.logger.debug("Connection " + socketToClient + " waiting for opcode");
               
        try {
            VirtualSocketAddress target = 
                new VirtualSocketAddress(inFromClient.readUTF());
            
            long timeout = inFromClient.readLong();
        
            Router.logger.debug("Connection " + socketToClient + " got:");
            Router.logger.debug("     target : " + target);            
            Router.logger.debug("     timeout: " + timeout);

            SocketAddressSet machine = target.machine();
            
            SocketAddressSet [] directions = parent.getDirections(machine);
            
            if (directions == null || directions.length == 0) { 
                Router.logger.warn("Connection " + socketToClient + " failed"
                        + " to get directions to: " + machine);
            } else { 
                succes = connectToTarget(target, timeout, directions);                
            }
            
        } catch (Exception e) {
            Router.logger.debug("Got exception in connect: " + e);
        }
        
        Router.logger.debug("Connection " + socketToClient + " setup succes: " + succes);
        
        if (succes) {         
            outToClient.write(REPLY_OK);
        } else { 
            outToClient.write(REPLY_FAILED);
        } 

        outToClient.flush();
        
        return succes;        
    }
                
    public void done(String label) {
        Router.logger.info("Received callback for forwarder " + label);
        
        // Check which forwarder thread produced the callback. Should be a 
        // real reference to label, so we don't have to use "equals".
        if (label == label1) {
            try { 
                outToTarget.flush();
                socketToTarget.shutdownOutput();            
                socketToClient.shutdownInput();
            } catch (Exception e) {
                Router.logger.warn("Failed to properly shutdown " + label1, e);                    
            }
        }
        
        if (label == label2) {
            try { 
                outToClient.flush();
                socketToClient.shutdownOutput();            
                socketToTarget.shutdownInput();
            } catch (Exception e) {
                Router.logger.warn("Failed to properly shutdown " + label2, e);
            }
        }
    
        synchronized (this) {          
            forwarderDone++; 
        
            if (forwarderDone == 2) { 
                Router.logger.info("Removing connections since they are done!");
                
                VirtualSocketFactory.close(socketToClient, outToClient, inFromClient);
                VirtualSocketFactory.close(socketToTarget, outToTarget, inFromTarget);
            } else { 
                Router.logger.info("Cannot remove connections yet!");
            }
        }         
    }

    private void startForwarders() { 
        
        label1 = "[" 
            + socketToClient.getRemoteSocketAddress() 
            + ":" 
            + socketToClient.getPort() 
            + " --> " 
            + socketToTarget.getRemoteSocketAddress() 
            + ":" 
            + socketToTarget.getPort() 
            + "]";
        
        label2 = "[" 
            + socketToTarget.getRemoteSocketAddress() 
            + ":" 
            + socketToTarget.getPort()         
            + " --> " 
            + socketToClient.getRemoteSocketAddress() 
            + ":"
            + socketToClient.getPort()         
            + "]";
    
        // TODO: thread pool ? 
        new Thread(new Forwarder(inFromClient, outToTarget, this, label1), 
                "ForwarderThread" + label1).start();
        
        new Thread(new Forwarder(inFromTarget, outToClient, this, label2), 
                "ForwarderThread" + label2).start();
    }
    
    public void run() { 
        
        try {            
            if (!connect()) {
                System.err.println("Failed to setup connection for " + socketToClient);
                VirtualSocketFactory.close(socketToClient, outToClient, inFromClient);
                return;
            }              

            // We have a connnection, so start forwarding!!
            startForwarders();
        } catch (Exception e) {
            // TODO: handle exception
            VirtualSocketFactory.close(socketToClient, outToClient, inFromClient);
            e.printStackTrace();
        }
    }
    
    public String toString() { 
        return "Connection from " + socketToClient; 
    }

    

}