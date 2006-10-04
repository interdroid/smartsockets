package ibis.connect.virtual.service;

import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.VirtualSocketAddress;

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
    
    public abstract SocketAddressSet getAddress() throws IOException; 
    
    public abstract void send(SocketAddressSet target, 
            SocketAddressSet targetProxy, String targetModule, int opcode, 
            String message);

    public abstract boolean registerService(String tag, VirtualSocketAddress address) throws IOException;
    
    public abstract Client [] clients() throws IOException;    
    public abstract Client [] clients(SocketAddressSet proxy) throws IOException;
    public abstract Client [] clients(String tag) throws IOException;    
    public abstract Client [] clients(SocketAddressSet proxy, String tag) throws IOException;
    
    public abstract Client [] localClients() throws IOException;    
    public abstract Client [] localClients(String tag) throws IOException;    
    
    public abstract SocketAddressSet [] proxies() throws IOException;    
    
    public abstract SocketAddressSet [] locateClient(String client) throws IOException;

    public abstract SocketAddressSet findSharedProxy(SocketAddressSet myMachine,
            SocketAddressSet targetMachine);
}
