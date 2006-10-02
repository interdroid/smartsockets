package ibis.connect.router.multiplex;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class RouterClient implements Protocol {

    private static Logger logger = 
        ibis.util.GetLogger.getLogger(RouterClient.class.getName());
    
    private static HashMap properties;       
    private static VirtualSocketFactory factory;     
    
    public final VirtualSocket s;
    public final DataOutputStream out;
    public final DataInputStream in;   
               
    RouterClient(VirtualSocket s, DataOutputStream out, DataInputStream in) {         
        this.s = s;        
        this.out = out;
        this.in = in;
    }
    
    public VirtualSocket connectToClient(VirtualSocketAddress target, 
            int timeout) throws IOException {
                        
        logger.info("Sending connect request to router!");
        
        out.writeUTF(target.toString());                
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
            logger.info("Connection setup returned junk!");
            return null;
        }
    }
               
    public static RouterClient connectToRouter(VirtualSocketAddress router, 
            int timeout) throws IOException {
        
        if (properties == null) {            
            logger.info("Initializing client-side router code");
            
            properties = new HashMap();
            properties.put("connect.module.skip", "routed");            
            factory = VirtualSocketFactory.getSocketFactory();          
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
        
        return new RouterClient(s, out, in);
    }
}
