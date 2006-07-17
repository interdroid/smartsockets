package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;
import ibis.connect.gossipproxy.connections.Connections;
import ibis.util.GetLogger;

import org.apache.log4j.Logger;

abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_PORT    = 17878;    
    protected static final int DEFAULT_TIMEOUT = 1000;
       
    protected final StateCounter state;         
    protected final Logger logger;
    
    protected final Connections connections;     
    protected final ProxyList knownProxies;
    
    protected final DirectSocketFactory factory;    
    
    protected SocketAddressSet local;
    protected String localAsString;
    
    protected CommunicationThread(String name, StateCounter state, 
            Connections connections, ProxyList knownProxies, 
            DirectSocketFactory factory) {
        
        super(name);        
        this.state = state;
        this.connections = connections;
        this.knownProxies = knownProxies;
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
