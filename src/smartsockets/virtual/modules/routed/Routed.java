package smartsockets.virtual.modules.routed;

import ibis.util.TypedProperties;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;

import smartsockets.Properties;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.ClientInfo;
import smartsockets.router.simple.RouterClient;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.modules.ConnectModule;

public class Routed extends ConnectModule {

    private static final int UPDATE_FREQUENCY = 10000;

    private static final int DEFAULT_TIMEOUT = 4000;   
    
    private LinkedList userSpecified = new LinkedList();    
    private LinkedList routers = new LinkedList();
    
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
            if (userSpecified.size() > 0) { 
                Iterator itt = userSpecified.iterator();
                
                while (itt.hasNext()) { 
                    VirtualSocketAddress a = (VirtualSocketAddress) itt.next();
                    if (!routers.contains(a)) { 
                        routers.addLast(a);
                    }
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
        VirtualSocketAddress a = (VirtualSocketAddress) routers.removeFirst();
        routers.addLast(a);
        
        return a;
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
                userSpecified.add(a);
            } catch (Exception e) {
                logger.info("Failed to add router \"" + tmp + "\"", e);
            }           
        }
    }

    public void initModule(Map properties) throws Exception {
        parseRouters(TypedProperties.stringProperty(Properties.ROUTERS));
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
    }

    public SocketAddressSet getAddresses() {
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map properties) throws ModuleNotSuitableException, IOException {

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
                c = RouterClient.connectToRouter(r, timeout);
            } catch (Exception e) {
                logger.info("Failed to connect to router \"" + r + "\"", e);
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
