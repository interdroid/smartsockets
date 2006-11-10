package smartsockets.hub.connections;

import ibis.util.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.state.HubList;

public abstract class BaseConnection implements Runnable {
    
    protected final DirectSocket s;
    protected final DataInputStream in;
    protected final DataOutputStream out; 
    
    protected Map<SocketAddressSet, BaseConnection> connections;
    protected final HubList knownHubs; 
          
    protected BaseConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList hubs) {
        
        this.s = s;
        this.in = in;
        this.out = out;
        this.connections = connections;
        this.knownHubs = hubs;
    }
        
    public void activate() {
        ThreadPool.createNew(this, getName());
    }
        
    public void run() { 

        boolean cont = true; 
        
        while (cont) {     
            cont = runConnection();
        } 
        
        // NOTE: Do NOT close the socket here, since it may still be in use!
        // DirectSocketFactory.close(s, out, in);        
    }
    
    protected abstract boolean runConnection();    
    protected abstract String getName();           
}
