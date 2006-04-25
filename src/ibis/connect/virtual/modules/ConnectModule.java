package ibis.connect.virtual.modules;

import ibis.connect.controlhub.CallBack;
import ibis.connect.controlhub.ServiceLink;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.util.Map;

import org.apache.log4j.Logger;

public abstract class ConnectModule implements CallBack {

    public final String name;
    public final boolean requiresServiceLink;
    
    protected VirtualSocketFactory parent;
    protected Logger logger;
    protected ServiceLink serviceLink;     
     
    protected ConnectModule(String name, boolean requiresServiceLink) { 
        this.name = name;
        this.requiresServiceLink = requiresServiceLink;
    }
            
    public void init(VirtualSocketFactory p, Logger l) throws Exception {
        
        parent = p;        
        logger = l;               
                
        l.info("Initializing module: " + name);
                        
        // Now perform the implementation-specific initialization.
        initModule();
    }
    
    public void startModule(ServiceLink sl) throws Exception { 
        
        if (requiresServiceLink) { 
            
            if (sl == null) {
                throw new Exception("Failed to initialize module: " + name 
                        + " (service link required)");                
            } 
              
            serviceLink = sl;
            serviceLink.register(name, this);        
        }   
        
        startModule();
    }
    
    public void gotMessage(SocketAddressSet src, int opcode, String message) {
        // Note: Default implementation. Should be extended by any module 
        // which requires use of service links         
        logger.warn("Module: "+ name + " got unexpected message from " + src);
    }
    
    public abstract void initModule() throws Exception; 

    public abstract void startModule() throws Exception; 
    
    public abstract SocketAddressSet getAddresses(); 
    
    public abstract VirtualSocket connect(VirtualSocketAddress target, int timeout,
            Map properties) throws ModuleNotSuitableException, IOException;        
}
