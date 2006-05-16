package ibis.connect.router.simple;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class RouterConnection implements Protocol {

    private static Logger logger = 
        ibis.util.GetLogger.getLogger(RouterConnection.class.getName());
    
    private static HashMap properties;
    private static int counter = 0;    
    private static VirtualSocketFactory factory;     
    private static SocketAddressSet localAddress;     
    
    public final VirtualSocket s;
    public final DataOutputStream out;
    public final DataInputStream in;   
    public final String key; 
               
    RouterConnection(VirtualSocket s, String key) throws IOException {

        this.s = s;
        this.key = key;
        
        out = new DataOutputStream(s.getOutputStream());
        in = new DataInputStream(s.getInputStream());               
    }

    public void close() { 
        Router.close(s, out, in);
    }
    
    public boolean sendConnect(String key, long timeout) throws IOException {
        out.write(CONNECT);
        out.writeLong(timeout);
        out.writeUTF(key);        
        out.flush();
        
        return waitForResult();
    }
    
    public boolean sendAccept(long timeout) throws IOException { 
        out.write(ACCEPT);
        out.writeLong(timeout);
        out.writeUTF(key);
        out.flush();
        
        return waitForResult();
    }
    
    private boolean waitForResult() {
        
        int result = FAILED;
        
        try { 
            result = in.read();
        } catch (Exception e) {
            // 
        }
        
        if (result == FAILED) { 
            close();
            return false;
        } else { 
            return true;
        } 
    } 
    
    private static synchronized String generateKey() {         
        return localAddress + "-" + (counter++);         
    }
        
    public static RouterConnection connect(VirtualSocketAddress router) throws IOException {
        
        if (properties == null) {            
            logger.info("Initializing client-side router code");
            
            properties = new HashMap();
            properties.put("connect.module.routed.skip", "true");            
            factory = VirtualSocketFactory.getSocketFactory();          
            localAddress = factory.getLocalHost();            
        }
        
        VirtualSocket s = null;
        
        try { 
            s = factory.createClientSocket(router, 0, properties);
        } catch (IOException e) {
            logger.info("Failed to connect to router at " + router);
            throw e;
        }
        
        return new RouterConnection(s, generateKey());                 
    }
}
