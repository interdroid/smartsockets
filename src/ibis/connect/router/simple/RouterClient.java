package ibis.connect.router.simple;

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
               
    RouterClient(VirtualSocket s) throws IOException {
        this.s = s;        
        out = new DataOutputStream(s.getOutputStream());
        in = new DataInputStream(s.getInputStream());               
    }

    public void close() { 
        VirtualSocketFactory.close(s, out, in);
    }
    
    public boolean connect(VirtualSocketAddress target, long timeout) 
        throws IOException {
                        
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
            return true;

        case REPLY_FAILED:
            logger.info("Connection setup failed!");
            return false;
        
        default:
            logger.info("Connection setup returned junk!");
            return false;
        }
    }
               
    public static RouterClient connect(VirtualSocketAddress router) throws IOException {
        
        if (properties == null) {            
            logger.info("Initializing client-side router code");
            
            properties = new HashMap();
            properties.put("connect.module.skip", "routed");            
            factory = VirtualSocketFactory.getSocketFactory();          
        }
        
        VirtualSocket s = null;
        
        try { 
            s = factory.createClientSocket(router, 0, properties);
        } catch (IOException e) {
            logger.info("Failed to connect to router at " + router);
            throw e;
        }
        
        return new RouterClient(s);                 
    }
}
