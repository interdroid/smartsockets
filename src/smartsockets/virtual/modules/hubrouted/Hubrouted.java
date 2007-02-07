package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import smartsockets.Properties;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.servicelink.ServiceLinkProtocol;
import smartsockets.hub.servicelink.VirtualConnectionCallBack;
import smartsockets.util.FixedSizeHashSet;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.modules.ConnectModule;

public class Hubrouted extends ConnectModule 
    implements VirtualConnectionCallBack {
    
    private static final int DEFAULT_TIMEOUT = 5000;   
   // private static final int DEFAULT_CLOSED_CONNECTION_CACHE = 10000;   
     
    private final Map<Long, HubRoutedVirtualSocket> sockets = 
       Collections.synchronizedMap(new HashMap<Long, HubRoutedVirtualSocket>());
    
    // TODO: this is a sanity check only, may be removed later!!
    //private final Set<Long> closedSockets = Collections.synchronizedSet(
    //            new FixedSizeHashSet<Long>(DEFAULT_CLOSED_CONNECTION_CACHE));
    
    private int localFragmentation = 64*1024;
    private int localBufferSize = 1024*1024;
    
    public Hubrouted() {
        super("ConnectModule(HubRouted)", true);
    }

    public void initModule(TypedProperties properties) throws Exception {
        localFragmentation = properties.getIntProperty(Properties.ROUTED_FRAGMENT, 
                localFragmentation);
        
        localBufferSize = properties.getIntProperty(Properties.ROUTED_BUFFER, 
                localBufferSize);
        
        if (localFragmentation > localBufferSize) { 
            
            logger.warn("Fragment size (" + localFragmentation 
                    + ") is larger than buffer size (" + localBufferSize 
                    + ") -> reducing fragment to: " + localBufferSize);
        
            localFragmentation = localBufferSize;       
        } else if (localFragmentation < localBufferSize) { 
            
            // Make sure that the fragmentation fits into the buffer a integral 
            // number of times!
            int mod = localBufferSize % localFragmentation;
            
            if (mod != 0) { 
                int div = localBufferSize / localFragmentation;                
                localBufferSize = (div+1) * localFragmentation;                
            }
        }
        
        // fragmentation -= 16; // leave some space for the headers...        
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
        
        serviceLink.registerVCCallBack(this);        
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
       
        final long deadline = System.currentTimeMillis() + timeout;
        int timeleft = timeout;
        
        // Create a socket first. Since this is a wrapper anyway, we can reuse 
        // it until we get a connection.
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, 
                 localFragmentation, localBufferSize, target, serviceLink, null);
        
        while (true) { 
        
            long index = serviceLink.getConnectionNumber();
          
            s.reset(index);
            sockets.put(index, s);
            
            try { 
                outgoingConnectionAttempts++;
                
                serviceLink.createVirtualConnection(index, tm, hub, 
                        target.port(), localFragmentation, localBufferSize, 
                        timeleft);
            
            } catch (IOException e) {
                // No connection to hub, or the send failed. Just retry ? 
                failedOutgoingConnections++;
                sockets.remove(index);
                
                if (logger.isInfoEnabled()) {
                    logger.info("Failed to create virtual connection to " 
                            + target + " (unknown host -> will retry)!");
                }
                  
                try { 
                    // TODO: deadline + exp backoff ?
                    Thread.sleep(1000);
                } catch (Exception x) {
                    // ignored
                }
                
                timeleft = (int) (deadline - System.currentTimeMillis()); 
                
                if (timeleft <= 0)  {
                    throw new ModuleNotSuitableException("Failed to create "
                            + "virtual connection to " + target + " within "
                            + timeout + " ms.");         
                }

                index = -1;
            } 
            
            if (index != -1) { 
                int result = s.waitForACK(timeout);
                
                switch (result) { 
            
                case 0: // success
                    acceptedOutgoingConnections++;
                    return s;
                    
                case -1: // timeout
                    failedOutgoingConnections++;
                    throw new SocketTimeoutException("ACK timed out!");
                    
                case ServiceLinkProtocol.ERROR_PORT_NOT_FOUND:
                    failedOutgoingConnections++;
                    throw new ConnectException("Remote port not found!");
                    
                case ServiceLinkProtocol.ERROR_CONNECTION_REFUSED:
                    failedOutgoingConnections++;
                    throw new ConnectException("Connection refused!");
                    
                case ServiceLinkProtocol.ERROR_UNKNOWN_HOST:
                    failedOutgoingConnections++;
                    throw new UnknownHostException("Unknown host " + target);
                    
                case ServiceLinkProtocol.ERROR_ILLEGAL_TARGET:
                    failedOutgoingConnections++;
                    throw new ConnectException("Connection refused!");
                }
            }   
        } 
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        return true;
    }

    public void connect(SocketAddressSet src, SocketAddressSet srcHub, int port,
            int remoteFragmentation, int remoteBufferSize, int timeout, 
            long index) {

        // Incoming connection...        
        incomingConnections++;
        
        // Get the serversocket (if it exists). 
        VirtualServerSocket ss = parent.getServerSocket(port);
        
        // Could not find it, so send a 'port not found' error back
        if (ss == null) { 
            logger.info("Failed find VirtualServerSocket(" + port + ")");
            rejectedIncomingConnections++;

            serviceLink.nackVirtualConnection(index, 
                    ServiceLinkProtocol.ERROR_PORT_NOT_FOUND);            
            return;
        }
        
        if (logger.isInfoEnabled()) { 
            logger.info("Hubrouted got new connection: " + index);
        }

        // Create a new socket 
        VirtualSocketAddress sa = new VirtualSocketAddress(src, 0, srcHub, null);
        
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, 
                localFragmentation, localBufferSize, remoteFragmentation, 
                remoteBufferSize, sa, serviceLink, index, null);
        
        sockets.put(index, s);
                
        if (!ss.incomingConnection(s)) { 
            sockets.remove(index);
            rejectedIncomingConnections++;
            serviceLink.nackVirtualConnection(index, 
                    ServiceLinkProtocol.ERROR_CONNECTION_REFUSED);
        } 
        
        /* ACK will be send during accept!
          
          else {
            acceptedIncomingConnections++;
            serviceLink.ackVirtualConnection(index, localFragmentation, localBufferSize); 
        }*/
    }

    public void disconnect(long vc) {
        
        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        if (s == null) { 
            // This can happen if we have just closed the socket...
            /* 
            if (!closedSockets.contains(vc)) {
                logger.warn("BAD!! Got disconnect from an unknown remote " 
                        + "socket!: " + vc);                                        
            }
            */
            return;
        } 
        
     //   closedSockets.add(vc);
        
        try { 
            s.close(false);
        } catch (Exception e) {
            logger.warn("Failed to close socket!", e);
        }
    }

    public void close(long vc) {

        HubRoutedVirtualSocket s = sockets.remove(vc);
        
        // logger.warn("Got close for socket!: " + vc);

        if (s == null) { 
            // This can happen if we have just been closed by the other side...
          /*  if (!closedSockets.contains(vc)) { 
                logger.warn("BAD!! Got close for an unknown local socket!: " 
                        + vc + " (" + closedSockets.size() + ")");
            }
*/
            return;
        } 

  //      closedSockets.add(vc);
        
        try {
            serviceLink.closeVirtualConnection(vc);
        } catch (Exception e) {
            logger.warn("Failed to forward close for virtual socket: " + vc, e); 
        }
    }

    public void connectACK(long index, int fragment, int buffer) {
        
        HubRoutedVirtualSocket s = sockets.get(index);
       
        boolean result = false;
        
        if (s == null) { 
            serviceLink.ackAckVirtualConnection(index, false);
            return;
        }
        
        s.connectACK(fragment, buffer);
    }

    protected void sendAckAck(long index, boolean result) { 
        serviceLink.ackAckVirtualConnection(index, result);
    }
    
    public void connectNACK(long index, byte reason) {
  
        HubRoutedVirtualSocket s = sockets.remove(index);
        
        if (s != null) { 
            s.connectNACK(reason);
        }
    }

    public void connectACKACK(long index, boolean succes) {
        
        HubRoutedVirtualSocket s = sockets.get(index);
        
        boolean result = false;
        
        if (s != null) {
            result = s.connectACKACK(succes);
        } 
        
        // If the handshake failed and someone is waiting, we need to send a 
        // close back.
        if (succes && !result) { 
            try { 
                serviceLink.closeVirtualConnection(index);
            } catch (Exception e) {
                logger.info("Failed to process ACKACK for socket!: " 
                        + index, e);
            }
        }
    }
    
    public void gotMessage(long index, byte[] data) {

        HubRoutedVirtualSocket s = sockets.get(index);
        
        // This can happen if we have just closed the socket.
        if (s == null) { 
            
            /*
            // Sanity check -- remove ASAP
            if (!closedSockets.contains(index)) { 
                logger.warn("BAD!! Got message for an unknown socket!: " + index 
                        + " size = " + data.length);
            } else { 
                logger.warn("BAD!! Got message for already closed socket!: " 
                        + index + " size = " + data.length);
            }*/
            return;
        } 

        s.message(data);
    }
    
    public void gotMessageACK(long index, int data) {
        
        HubRoutedVirtualSocket s = sockets.get(index);
        
        // This can happen if we have just closed the socket.
        if (s == null) {
            // Sanity check -- remove ASAP
/*            if (!closedSockets.contains(index)) { 
                logger.warn("BAD!! Got message ACK for an unknown socket!: " 
                        + index + " data = " + data);
            } */
            return;
        }
            
        s.messageACK(data);
    }
}
