/**
 * 
 */
package ibis.connect.router.simple;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;

class Connection extends Thread implements Protocol {       

    private final Router parent;
    
    private VirtualSocket socketToClient;
    private DataOutputStream outToClient;
    private DataInputStream inFromClient;
    
    private VirtualSocket socketToTarget;
    private DataOutputStream outToTarget;
    private DataInputStream inFromTarget;
        
    private boolean done = false;
   
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
    
    private VirtualSocketAddress getAddress(String router) throws UnknownHostException {        
        String tmp = router.substring(0, router.lastIndexOf('#'));        
        return new VirtualSocketAddress(tmp);
    }
    
    private boolean connectViaRouter(String router, VirtualSocketAddress target,
            long timeout) {

        boolean succes = false;
        
        try {
            Router.logger.debug("Connecting to router: " + router);
                        
            VirtualSocketAddress r = getAddress(router);            
           
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

        int startIndex = 0;
        
        if (directions[0].equals(proxy)) {
             
            Router.logger.debug("Client is local to my proxy!!");
                        
            if (connect(target, timeout)) { 
                Router.logger.debug("Created local connection to client " 
                        + target);
                return true;
            }
            
            startIndex++;
            
        } else { 
            
            Router.logger.debug("Client is NOT local to my proxy!!");  
            Router.logger.debug("Directions to client machine: "); 

            for (int i=0;i<directions.length;i++) {
                Router.logger.debug(" " + i + " : " + directions[i]);
            }
        } 
        
        // The target wasn't local. We now traverse each of the proxies one by
        // one, and check if we can find any router processes associated with 
        // them. If ther are, we try to connect to the target via one of these
        // routers...        
        for (int i=startIndex;i<directions.length;i++) {
            
            String [] result = null;
            
            try { 
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
            
    public void run() { 
        
        try {            
            if (!connect()) {
                System.err.println("Failed to setup connection for " + socketToClient);
                VirtualSocketFactory.close(socketToClient, outToClient, inFromClient);
                return;
            }              
         
            
            
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