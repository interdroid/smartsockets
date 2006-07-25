package ibis.connect.virtual.service;

import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import ibis.connect.direct.SocketAddressSet;

public abstract class ServiceLink {
        
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLink.class.getName());
         
    protected final HashMap callbacks = new HashMap();
    
    public synchronized void register(String identifier, CallBack callback) { 

        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, callback);              
    }
    
    protected synchronized void registerCallback(String identifier, SimpleCallBack cb) { 
        
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, cb);        
    }
    
    protected synchronized Object findCallback(String identifier) {         
        return callbacks.get(identifier);        
    }
    
    protected synchronized void removeCallback(String identifier) {         
        callbacks.remove(identifier);        
    }
    
    public abstract SocketAddressSet getAddress(); 
    
    public abstract void send(SocketAddressSet target, String targetModule, 
            int opcode, String message);

    public abstract String [] clients() throws IOException;    
    public abstract String [] clients(SocketAddressSet proxy) throws IOException;
    public abstract String [] clients(String tag) throws IOException;    
    public abstract String [] clients(SocketAddressSet proxy, String tag) throws IOException;
    
    public abstract String [] localClients() throws IOException;    
    public abstract String [] localClients(String tag) throws IOException;    
    
    public abstract SocketAddressSet [] proxies() throws IOException;    
    
    public abstract SocketAddressSet [] directionToClient(String client, 
            String tag) throws IOException;    
    
}
