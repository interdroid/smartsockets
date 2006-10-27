package smartsockets.router.simple;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

public class RouterClient implements Protocol {

    private static Logger logger = 
        ibis.util.GetLogger.getLogger(RouterClient.class.getName());
    
    private static long clientID = 0;
    private static HashMap properties;       
    
    public final long id;
    public final VirtualSocket s;
    public final DataOutputStream out;
    public final DataInputStream in;   
    public final VirtualSocketFactory factory;     
               
    RouterClient(long id, VirtualSocketFactory factory, VirtualSocket s, 
            DataOutputStream out, DataInputStream in) {
        
        this.id = id;
        this.s = s;        
        this.out = out;
        this.in = in;
        this.factory = factory;
    }
    
    public VirtualSocket connectToClient(VirtualSocketAddress target, 
            int timeout) throws IOException {
                        
        logger.info("Sending connect request to router!");
        
        out.writeUTF(factory.getLocalHost().toString());                
        out.writeUTF(target.toString());             
        out.writeLong(id);
        out.writeLong(timeout);
        out.flush();

        logger.info("Waiting for router reply...");
                
        // TODO set timeout!!!
        int result = in.readByte();
        
        switch (result) {
        case REPLY_OK:
            logger.info("Connection setup succesfull!");            
            return s;

        case REPLY_FAILED:
            logger.info("Connection setup failed!");
            return null;
        
        default:
            logger.info("Connection setup returned junk (2) !: " + result);
            return null;
        }
    }
               
    public static synchronized long getID() { 
        return clientID++;
    }
    
    public static RouterClient connectToRouter(VirtualSocketAddress router, 
            VirtualSocketFactory factory, int timeout) throws IOException {
        
        if (properties == null) {            
            logger.info("Initializing client-side router code");            
            properties = new HashMap();
            properties.put("connect.module.skip", "routed");            
        }
        
        VirtualSocket s = null;
        DataOutputStream out = null;
        DataInputStream in = null;   
            
        try { 
            s = factory.createClientSocket(router, timeout, properties);
            out = new DataOutputStream(s.getOutputStream());
            in = new DataInputStream(s.getInputStream());                                   
        } catch (IOException e) {
            logger.info("Failed to connect to router at " + router);
            VirtualSocketFactory.close(s, out, in);
            throw e;
        }
        
        return new RouterClient(getID(), factory, s, out, in);
    }
}
