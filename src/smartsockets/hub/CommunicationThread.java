package smartsockets.hub;

import ibis.util.GetLogger;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.connections.Connections;
import smartsockets.hub.state.HubList;
import smartsockets.hub.state.StateCounter;

abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_PORT    = 17878;    
    protected static final int DEFAULT_TIMEOUT = 1000;
       
    protected final StateCounter state;         
    protected final Logger logger;
    
    protected final Connections connections;     
    protected final HubList knownHubs;
    
    protected final DirectSocketFactory factory;    
    
    protected SocketAddressSet local;
    protected String localAsString;
    
    protected CommunicationThread(String name, StateCounter state, 
            Connections connections, HubList knownHubs, 
            DirectSocketFactory factory) {
        
        super(name);        
        this.state = state;
        this.connections = connections;
        this.knownHubs = knownHubs;
        this.factory = factory;        
        logger = GetLogger.getLogger(this.getClass().getName());                   
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
