package ibis.smartsockets.hub.connections;

import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.state.HubList;
import ibis.util.ThreadPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Map;


public abstract class BaseConnection implements Runnable {
    
    protected final DirectSocket s;
    protected final DataInputStream in;
    protected final DataOutputStream out; 
    
    protected Map<DirectSocketAddress, BaseConnection> connections;
    
    protected final HubList knownHubs; 

    protected BaseConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, 
            Map<DirectSocketAddress, BaseConnection> connections, 
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
    }
    
    protected abstract boolean runConnection();    
    protected abstract String getName();           
    public abstract void printStatistics(); 
    
}
