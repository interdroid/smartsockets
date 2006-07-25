/**
 * 
 */
package ibis.connect.gossipproxy.router;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

class Connection extends Thread implements Protocol {       

    private static int BUFFER_SIZE = 1024; 
        
    private final Router parent;
    
    private final VirtualSocket sc;
    private final DataOutputStream out;
    private final DataInputStream in;
    
    private DataOutputStream targetOut;
    
    private boolean done = false;
       
    private byte [] buffer;
    private long bytesRead;
    
    boolean connected = false;
            
    Connection(VirtualSocket sc, Router parent) throws IOException {
        
        Router.logger.debug("Created new connection to " + sc);
                
        this.sc = sc;
        this.parent = parent;
        
        // No extra buffer here, since we will mostly send byte[]
        out = new DataOutputStream(sc.getOutputStream());
        in = new DataInputStream(sc.getInputStream());
        
        buffer = new byte[BUFFER_SIZE];
    }
              
    private boolean connect() throws IOException { 
         
        boolean succes = false;
        
        Router.logger.debug("Connection " + sc + " waiting for opcode");
               
        try {
            VirtualSocketAddress target = 
                new VirtualSocketAddress(in.readUTF());
            
            long timeout = in.readLong();
        
            Router.logger.debug("Connection " + sc + " got:");
            Router.logger.debug("     target : " + target);            
            Router.logger.debug("     timeout: " + timeout);

            SocketAddressSet machine = target.machine();
            
            
            
            
            
            // TODO: forward connection here!!
        
        } catch (Exception e) {
            System.err.println("Got exception in connect: " + e);
        }

        System.err.println("Connection " + sc + " setup succes: " + succes);
                
        if (succes) {         
            out.write(REPLY_OK);
        } else { 
            out.write(REPLY_FAILED);
        } 
        out.flush();
        
        return succes;        
    }
            
    public void run() { 
        
        try {            
            if (!connect()) {
                System.err.println("Failed to setup connection for " + sc);
                VirtualSocketFactory.close(sc, out, in);
                return;
            }              
         
            
            
        } catch (Exception e) {
            // TODO: handle exception
            VirtualSocketFactory.close(sc, out, in);
            e.printStackTrace();
        }
    }
    
    public String toString() { 
        return "Connection from " + sc; 
    }
    

}