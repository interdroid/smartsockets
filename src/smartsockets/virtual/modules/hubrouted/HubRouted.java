package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
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
        SocketAddressSet hub = target.hub();
           
        /*
        if (tm.sameMachine(parent.getLocalHost())) { 
            throw new ModuleNotSuitableException(module + ": Cannot set up " +
                "a connection to myself!"); 
        }
        */
        
        if (timeout == 0) { 
            timeout = DEFAULT_TIMEOUT; 
        }
       
        long index = -1;
        long endTime = System.currentTimeMillis() + timeout;
        boolean timeLeft = true;
        
        HubRoutedVirtualSocket s = null;
        
        while (index == -1 && timeLeft) { 
        
            index = serviceLink.getConnectionNumber();
            
            // As soon as we get an index back we create the socket. Otherwise 
            // there may be a race between the accept being handled and the 
            // receipt of the first message...
            s = new HubRoutedVirtualSocket(this, target, serviceLink, index, null);
            
            synchronized (this) {
                sockets.put(index, s);
            }
            
            try { 
                serviceLink.createVirtualConnection(index, tm, hub, 
                        Integer.toString(target.port()), timeout);
            
            } catch (UnknownHostException e) {
                synchronized (this) {
                    sockets.remove(index);
                }
                   
                index = -1;
    
                // The target machine isn't known (yet) -- retry as long as we 
                // stick to the timeout....
                
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to create virtual connection to " 
                            + target + " (unknown host -> will retry)!");
                }
                    
                try { 
                    // TODO: fix deadline
                    Thread.sleep(1000);
                } catch (Exception x) {
                    // ignored
                }
                
                if (timeout > 0) { 
                    timeLeft = System.currentTimeMillis() < endTime; 
                } 
             
                if (!timeLeft)  {
                    throw new ModuleNotSuitableException("Failed to create virtual " +
                            "connection to " + target + " within " + timeout 
                            + " ms.", e);         
                }
                
            } catch (ConnectException e) {
                
                //if (logger.isInfoEnabled()) {
                    logger.warn(parent.getVirtualAddressAsString() + ": Failed to create virtual connection to " 
                            + target + " (connection refused -> giving up)");
                //}
        
                // The target refused the connection (this is user error)
                throw new ConnectException("Connection refused by: " + target);
                
            } catch (IOException e) {
              
                //if (logger.isInfoEnabled()) {
                    logger.warn(parent.getVirtualAddressAsString() + ": Failed to create virtual connection to " 
                            + target + " (giving up)");
                //}
            
                throw new ModuleNotSuitableException("Failed to create virtual " +
                        "connection to " + target, e);
            }
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
            logger.info("Hubrouted got new connection: " + index);
        }
        
        VirtualSocketAddress sa = new VirtualSocketAddress(src, 0);
        
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, sa, 
                serviceLink, index, null);
        
        if (!ss.incomingConnection(s)) { 
            
           // logger.warn("Connection " + index + " was refused by ss!");
            // not accepted 
            return false;            
        }
        
        //logger.warn("Connection " + index + " was accepted by ss!");
        
        synchronized (this) {
            sockets.put(index, s);
        }
        
        return true;
    }

    public synchronized void disconnect(long vc) {
        
        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        if (s == null) { 
            // This can happen if we have just closed the socket...
        //    if (logger.isInfoEnabled()) {             
                logger.warn("Got disconnect for an unknown socket!: " + vc);
        //   / }
            return;
        } 
        
       // if (logger.isDebugEnabled()) { 
        //    logger.warn("Got disconnect for: " + vc);
      //  }
        
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
           // if (logger.isInfoEnabled()) {             
                logger.warn("Got message for an unknown socket!: " + vc 
                        + " size = " + data.length);
           // }
            return;
        } 

        s.message(data);
    }

    public synchronized void close(long vc) {

        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        //logger.warn("Got close for socket!: " + vc);
        
        if (s == null) { 
            // This can happen if we have just been closed by the other side...
        //    if (logger.isInfoEnabled()) {             
                logger.warn("Got close from an unknown socket!: " + vc);
         //   }
            return;
        } 
        
        s.close();
        
       // if (logger.isDebugEnabled()) { 
        //    logger.warn("Got close for: " + vc);
        //}
    }
}
