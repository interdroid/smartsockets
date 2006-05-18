package ibis.connect.virtual.modules.routed;

import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.Properties;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;
import ibis.util.TypedProperties;

public class Routed extends ConnectModule {

    private static final int DEFAULT_TIMEOUT = 4000;   
    
    private Direct direct;    
    
    public Routed() {
        super("ConnectModule(Routed)", true);
    }
    
    private synchronized void addRouter(VirtualSocketAddress address) { 
        
    }
    
    private synchronized void removeRouter(VirtualSocketAddress address) { 
        
    }
    
    private synchronized boolean knownRouter(VirtualSocketAddress address) { 
        return false;
    }
    
    private void parseRouters(String routers) { 
        
        if (routers == null || routers.trim().length() == 0) { 
            return;
        }
        
        StringTokenizer st = new StringTokenizer(routers, ",");
        
        while (st.hasMoreTokens()) {           
            String tmp = st.nextToken();
            
            try { 
                VirtualSocketAddress a = new VirtualSocketAddress(tmp);                
                logger.info("Found router \"" + tmp + "\"");                
                addRouter(a);
            } catch (Exception e) {
                logger.info("Failed to add router \"" + tmp + "\"", e);
            }           
        }
    }

    public void initModule() throws Exception {
        parseRouters(TypedProperties.stringProperty(Properties.ROUTERS));
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(name + ": no service link available!");       
        }

    }

    public SocketAddressSet getAddresses() {
        // TODO Auto-generated method stub
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map properties) throws ModuleNotSuitableException, IOException {

        if (timeout == 0 || timeout > DEFAULT_TIMEOUT) { 
            timeout = DEFAULT_TIMEOUT; 
        }
        
        return null;
    
    }

}
