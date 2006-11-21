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
    
    private final HashMap<Long, HubRoutedVirtualSocket> sockets = 
        new HashMap<Long, HubRoutedVirtualSocket>();
    
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
       

        long index = -1;
        
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

        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, target, 
                serviceLink, index, null);
        
        synchronized (this) {
            sockets.put(index, s);
        }
            
        return s; 
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        return true;
    }

    public boolean connect(SocketAddressSet src, String info, int timeout, 
            long index) {
        
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
        
        if (logger.isInfoEnabled()) { 
            logger.info("Got new connection: " + index);
        }
        
        VirtualSocketAddress sa = new VirtualSocketAddress(src, 0);
        
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, sa, 
                serviceLink, index, null);
        
        if (!ss.incomingConnection(s)) { 
            // not accepted 
            return false;            
        }
        
        synchronized (this) {
            sockets.put(index, s);
        }
        
        return true;
    }

    public synchronized void disconnect(long vc) {
        
        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        if (s == null) { 
            // This can happen if we have just closed the socket...
            if (logger.isInfoEnabled()) {             
                logger.info("Got disconnect for an unknown socket!: " + vc);
            }
            return;
        } 
        
        if (logger.isDebugEnabled()) { 
            logger.debug("Got disconnect for: " + vc);
        }
        
        try { 
            s.close();
        } catch (Exception e) {
            logger.warn("Failed to close socket!", e);
        }
    }

    public synchronized void gotMessage(long vc, byte[] data) {

        HubRoutedVirtualSocket s = sockets.get(vc);
        
        if (s == null) { 
            // This can happen if we have just been closed by the other side...
            if (logger.isInfoEnabled()) {             
                logger.info("Got message for an unknown socket!: " + vc);
            }
            return;
        } 

        s.message(data);
    }

    public synchronized void close(long vc) {

        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        if (s == null) { 
            // This can happen if we have just been closed by the other side...
            if (logger.isInfoEnabled()) {             
                logger.info("Got disconnect from an unknown socket!: " + vc);
            }
            return;
        } 
        
        if (logger.isDebugEnabled()) { 
            logger.debug("Got disconnect for: " + vc);
        }
    }
}
