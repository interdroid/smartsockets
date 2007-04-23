package smartsockets.hub;

import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.DirectSocketAddress;
import smartsockets.hub.connections.BaseConnection;
import smartsockets.hub.connections.VirtualConnections;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_TIMEOUT = 10000;
       
    protected final StateCounter state;         
    
    protected static final Logger hublogger = 
        Logger.getLogger("smartsockets.hub"); 
        
    protected final Map<DirectSocketAddress, BaseConnection> connections;     
    
    protected final HubList knownHubs;
    protected final VirtualConnections virtualConnections;
    protected final DirectSocketFactory factory;    
    
    protected DirectSocketAddress local;
    protected String localAsString;
    
    protected CommunicationThread(String name, StateCounter state, 
            Map<DirectSocketAddress, BaseConnection> connections, 
            HubList knownHubs, VirtualConnections vcs, 
            DirectSocketFactory factory) {
        
        super(name);        
        this.state = state;
        this.connections = connections;
        this.knownHubs = knownHubs;
        this.virtualConnections = vcs;
        this.factory = factory;        
    }
    
    protected void setLocal(DirectSocketAddress local) { 
        this.local = local;
        this.localAsString = local.toString();        
    }
    
    protected DirectSocketAddress getLocal() {
        return local;
    }
    
    protected String getLocalAsString() {
        return localAsString;
    }
}
