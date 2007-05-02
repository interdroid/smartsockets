package ibis.smartsockets.hub;

import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.hub.connections.BaseConnection;
import ibis.smartsockets.hub.connections.VirtualConnections;
import ibis.smartsockets.hub.state.HubList;
import ibis.smartsockets.hub.state.StateCounter;

import java.util.Map;

import org.apache.log4j.Logger;


abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_TIMEOUT = 10000;
       
    protected final StateCounter state;         
    
    protected static final Logger hublogger = 
        Logger.getLogger("ibis.smartsockets.hub"); 
        
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
