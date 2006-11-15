package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.VirtualConnectionCallBack;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.modules.ConnectModule;

public class HubRouted extends ConnectModule 
    implements VirtualConnectionCallBack {

    private static final int DEFAULT_TIMEOUT = 4000;   
    
    private final HashMap<Integer, HubRoutedVirtualSocket> sockets = 
        new HashMap<Integer, HubRoutedVirtualSocket>();
    
    public HubRouted() {
        super("ConnectModule(HubRouted)", true);
    }

    public void initModule(Map properties) throws Exception {
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
        
        serviceLink.register("__virtual", this);        
    }

    public SocketAddressSet getAddresses() {
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map properties) throws ModuleNotSuitableException, IOException {

        // First check if we are trying to connect to ourselves (which makes no 
        // sense for this module...
        SocketAddressSet tm = target.machine(); 
           
        /*
        if (tm.sameMachine(parent.getLocalHost())) { 
            throw new ModuleNotSuitableException(module + ": Cannot set up " +
                "a connection to myself!"); 
        }
        */
        
        if (timeout == 0 || timeout > DEFAULT_TIMEOUT) { 
            timeout = DEFAULT_TIMEOUT; 
        }
       

        int index = -1;
        
        try { 
            index = serviceLink.createVirtualConnection(tm,
                    Integer.toString(target.port()), timeout);
            
        } catch (Exception e) {
            if (logger.isInfoEnabled()) {
                logger.info("Failed to create virtual connection to " 
                        + target, e);
            }
            
            throw new ModuleNotSuitableException("Failed to create virtual " +
                    "connection to " + target, e);                    
        } 

        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(target, 
                serviceLink, index, null);
        
        sockets.put(index, s);
        
        return s; 
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        return true;
    }

    public boolean connect(SocketAddressSet src, String info, int timeout, 
            int vc, String replyID) {
        
        // Incoming connect, find the port...        
        int port = -1;
        
        try {         
            port = Integer.parseInt(info);
        } catch (Exception e) {
            logger.info("Failed to parse port of incoming connection!");
            return false;
        }
        
        // Get the serversocket (if it exists). 
        VirtualServerSocket ss = parent.getServerSocket(port);
        
        if (ss == null) { 
            logger.info("Failed find VirtualServerSocket(" + port + ")");
            return false;
        }
        
        logger.warn("Got new connection: " + vc);
        
        VirtualSocketAddress sa = new VirtualSocketAddress(src, 0);
        
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(sa, serviceLink, 
                vc, replyID, null);
        
        if (!ss.incomingConnection(s)) { 
            // not accepted 
            return false;            
        }
        
        sockets.put(vc, s);
        
        return true;
    }

    public void disconnect(int vc) {
        
        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        if (s == null) { 
            logger.warn("Got disconnect for an unknown socket!: " + vc);
            return;
        } 

        try { 
            s.close();
        } catch (Exception e) {
            logger.warn("Failed to close socket!", e);
        }
    }

    public void gotMessage(int vc, byte[] data) {

        HubRoutedVirtualSocket s = sockets.get(vc);
        
        if (s == null) { 
            logger.warn("Got message for an unknown socket!: " + vc);
            return;
        } 

        s.message(data);
    }
}
