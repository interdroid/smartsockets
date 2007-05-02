package ibis.smartsockets.virtual.modules.routed;

import ibis.smartsockets.Properties;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.ClientInfo;
import ibis.smartsockets.router.simple.RouterClient;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.ModuleNotSuitableException;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.modules.ConnectModule;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;


public class Routed extends ConnectModule {

    private static final int UPDATE_FREQUENCY = 10000;

    private static final int DEFAULT_TIMEOUT = 4000;   
    
    private LinkedList<VirtualSocketAddress> userSpecified = 
        new LinkedList<VirtualSocketAddress>();    
    
    private LinkedList<VirtualSocketAddress> routers = 
        new LinkedList<VirtualSocketAddress>();
    
    private long lastUpdate; 
    
    public Routed() {
        super("ConnectModule(Routed)", true);
    }
        
    private synchronized void removeRouter(VirtualSocketAddress address) {         
        routers.remove(address);        
    }
    
    private void getRoutersFromServiceLink() {

        // This method retrieves the current set of local routers from the 
        // service link.                        
        try { 
            
            // Start by clearing our memory
            routers.clear();

            // Next get the routers from the service link 
            ClientInfo [] r = serviceLink.localClients("router");
        
            // Then add them to our cache
            if (r != null && r.length > 0) {
                for (int i=0;i<r.length;i++) {                     
                    ClientInfo c = r[i];
                    routers.addLast(c.getPropertyAsAddress("router"));
                }                    
            }
            
            // Finally, add all routers that the user gave us, making sure that 
            // we don't known them yet...   
            for (VirtualSocketAddress a : userSpecified) { 
                if (!routers.contains(a)) { 
                    routers.addLast(a);
                }
            }
            
        } catch (IOException e) {
            logger.warn("Failed to retrieve routers from service link!", e);
        }
    
        lastUpdate = System.currentTimeMillis();        
    }
    
    private synchronized VirtualSocketAddress getRouter(boolean allowUpdate) {
        
        // Get an update from the servicelink if required....
        if (allowUpdate && (routers.size() == 0 || 
                (lastUpdate+UPDATE_FREQUENCY) < System.currentTimeMillis())) {            
            getRoutersFromServiceLink();
        }
        
        // If we didn't find any routers, we return null;
        if (routers.size() == 0) {
            return null;
        }
        
        // We get the first router and move it to the end of the list. This way,
        // we will use the routers in a round-robin fashion. 
        VirtualSocketAddress a = routers.removeFirst();
        routers.addLast(a);
        
        return a;
    }
        
    private void parseRouters(String [] routers) { 
        
        if (routers == null || routers.length == 0) { 
            return;
        }
        
        for (String tmp : routers) { 
            try { 
                VirtualSocketAddress a = new VirtualSocketAddress(tmp);
                if (logger.isInfoEnabled()) {
                    logger.info("Found router \"" + tmp + "\"");
                }
                userSpecified.add(a);
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to add router \"" + tmp + "\"", e);
                }
            }           
        }
    }

    public void initModule(TypedProperties properties) throws Exception {
        
        // TODO: This only reads the default ???
        TypedProperties p = Properties.getDefaultProperties();
        parseRouters(p.getStringList(Properties.ROUTERS));
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
    }

    public DirectSocketAddress getAddresses() {
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map<String, Object> properties) 
        throws ModuleNotSuitableException, IOException {

        // First check if we are trying to connect to ourselves (which makes no 
        // sense for this module...
        /*
        if (target.machine().sameMachine(parent.getLocalHost())) { 
            throw new ModuleNotSuitableException(module + ": Cannot set up " +
                "a connection to myself!"); 
        }
       */
        
        if (timeout == 0 || timeout > DEFAULT_TIMEOUT) { 
            timeout = DEFAULT_TIMEOUT; 
        }
        
        VirtualSocketAddress r = getRouter(true);
        RouterClient c = null;
                
        while (r != null && c == null) {         
            try { 
                c = RouterClient.connectToRouter(r, parent, timeout);
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to connect to router \"" + r + "\"", e);
                }
                removeRouter(r);
                r = getRouter(false);
            }                        
        } 
        
        if (c == null) { 
            throw new ModuleNotSuitableException("No (working) routers could"
                    + " be found!");
        }
             
        // TODO: refine result here ? 
        return c.connectToClient(target, timeout);
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        return true;
    }
}
