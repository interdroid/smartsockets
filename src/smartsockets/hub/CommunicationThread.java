package smartsockets.hub;

import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.connections.BaseConnection;
import smartsockets.hub.connections.VirtualConnections;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_TIMEOUT = 1000;
       
    protected final StateCounter state;         
    
    protected static final Logger hublogger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub"); 
        
    protected final Map<SocketAddressSet, BaseConnection> connections;     
    
    protected final HubList knownHubs;
    protected final VirtualConnections virtualConnections;
    protected final DirectSocketFactory factory;    
    
    protected SocketAddressSet local;
    protected String localAsString;
    
    protected CommunicationThread(String name, StateCounter state, 
            Map<SocketAddressSet, BaseConnection> connections, 
            HubList knownHubs, VirtualConnections vcs, 
            DirectSocketFactory factory) {
        
        super(name);        
        this.state = state;
        this.connections = connections;
        this.knownHubs = knownHubs;
        this.virtualConnections = vcs;
        this.factory = factory;        
    }
    
    protected void setLocal(SocketAddressSet local) { 
        this.local = local;
        this.localAsString = local.toString();        
    }
    
    protected SocketAddressSet getLocal() {
        return local;
    }
    
    protected String getLocalAsString() {
        return localAsString;
    }
}
