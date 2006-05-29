package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.VirtualSocketFactory;
import ibis.util.GetLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import org.apache.log4j.Logger;

abstract class CommunicationThread extends Thread {

    protected static final int DEFAULT_PORT    = 17878;    
    protected static final int DEFAULT_TIMEOUT = 1000;
    protected static final HashMap CONNECT_PROPERTIES = new HashMap();    
            
    protected final Logger logger;     
    protected final ProxyList knownProxies;
    protected final VirtualSocketFactory factory;    
    
    protected VirtualSocketAddress local;
    protected String localAsString;
    
    protected CommunicationThread(ProxyList knownProxies,
            VirtualSocketFactory factory) { 
                
        this.knownProxies = knownProxies;
        this.factory = factory;        
        logger = GetLogger.getLogger(this.getClass().getName());                
        CONNECT_PROPERTIES.put("allowed.modules", "direct");            
    }
    
    protected void setLocal(VirtualSocketAddress local) { 
        this.local = local;
        this.localAsString = local.toString();        
    }
    
    protected VirtualSocketAddress getLocal() {
        return local;
    }
    
    protected String getLocalAsString() {
        return localAsString;
    }
        
    protected void close(VirtualSocket s, InputStream in, OutputStream out) { 
        
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
                
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            s.close();
        } catch (Exception e) {
            // ignore
        }
    }  
}
