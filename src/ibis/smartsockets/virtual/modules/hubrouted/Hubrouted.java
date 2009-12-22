package ibis.smartsockets.virtual.modules.hubrouted;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.servicelink.ServiceLinkProtocol;
import ibis.smartsockets.hub.servicelink.VirtualConnectionCallBack;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.NonFatalIOException;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.modules.ConnectModule;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class Hubrouted extends ConnectModule 
    implements VirtualConnectionCallBack {
    
    private static final int DEFAULT_CONNECT_TIMEOUT = 5000;   
   // private static final int DEFAULT_CLOSED_CONNECTION_CACHE = 10000;   
     
    private final Map<Long, HubRoutedVirtualSocket> sockets = 
       Collections.synchronizedMap(new HashMap<Long, HubRoutedVirtualSocket>());
      
    private int localFragmentation = 8*1024-16;
    private int localBufferSize = 1024*1024;
    private int localMinimalACKSize = localBufferSize / 4;
        
    public Hubrouted() {
        super("ConnectModule(HubRouted)", true);
    }

    public void initModule(TypedProperties properties) throws Exception {
        localFragmentation = properties.getIntProperty(SmartSocketsProperties.ROUTED_FRAGMENT, 
                localFragmentation);
        
        localBufferSize = properties.getIntProperty(SmartSocketsProperties.ROUTED_BUFFER, 
                localBufferSize);
        
        localMinimalACKSize = properties.getIntProperty(
                SmartSocketsProperties.ROUTED_MIN_ACK, localBufferSize/4);
        
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
        
        if (localMinimalACKSize > localBufferSize) { 
            logger.warn("Minimal ACK size (" + localMinimalACKSize
                    + ") is larger than buffer size (" + localBufferSize 
                    + ") -> reducing minimal ACK size to: " + localBufferSize);
        
            localFragmentation = localBufferSize;       
        }
        
        if (logger.isInfoEnabled()) { 
            logger.info("Using local fragment size: " + localFragmentation);
            logger.info("Using local buffer size  : " + localBufferSize);
            logger.info("Using minimal ACK size  : " + localMinimalACKSize);
        }
    }

    public void startModule() throws Exception {

        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
        
        serviceLink.registerVCCallBack(this);        
    }

    public DirectSocketAddress getAddresses() {
        return null;
    }

    public VirtualSocket connect(VirtualSocketAddress target, int timeout, 
            Map<String, Object> properties) throws NonFatalIOException, 
            IOException {
        
        // First check if we are trying to connect to ourselves (which makes no 
        // sense for this module...
        DirectSocketAddress tm = target.machine(); 
        DirectSocketAddress hub = target.hub();
           
        /*
        if (tm.sameMachine(parent.getLocalHost())) { 
            throw new NonFatalIOException(module + ": Cannot set up " +
                "a connection to myself!"); 
        }
        */
        
        if (timeout == 0) { 
            timeout = DEFAULT_CONNECT_TIMEOUT; 
        }
       
        final long deadline = System.currentTimeMillis() + timeout;
        int timeleft = timeout;
        
        // Create a socket first. Since this is a wrapper anyway, we can reuse 
        // it until we get a connection.
        HubRoutedVirtualSocket s = new HubRoutedVirtualSocket(this, 
                 localFragmentation, localBufferSize, localMinimalACKSize, 
                 target, serviceLink, null);
        
        while (true) { 
        
            long index = serviceLink.getConnectionNumber();

            s.reset(index);
            sockets.put(index, s);
            
            try { 
          //      outgoingConnectionAttempts++;
                
                serviceLink.createVirtualConnection(index, tm, hub, 
                        target.port(), localFragmentation, localBufferSize, 
                        timeleft);
            
             //   return s;               
            } catch (IOException e) {
                // No connection to hub, or the send failed. Just retry ? 
           //     failedOutgoingConnections++;
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
                    throw new NonFatalIOException(
                            new SocketTimeoutException("Failed to create "
                            + "virtual connection within "
                            + timeout + " ms. (" + e + ")"));         
                }

                index = -1;
            } 
            
            if (index != -1) { 
                int result = s.waitForACK(timeout);
                
                switch (result) { 
            
                case 0: // success
            //        acceptedOutgoingConnections++;
                    return s;
                    
                case ServiceLinkProtocol.ERROR_SERVER_OVERLOAD:
                    // This one should be handled on a higher level, where we 
                    // have a clue about timeouts
                    s.setTargetOverload();
                    return s;
                    
                case -1: 
                    // Timeout. Assume it is this module's fault
          //          failedOutgoingConnections++;
                    throw new NonFatalIOException(
                            new SocketTimeoutException("Failed to create "
                            + "virtual connection within " + timeout + " ms."));      
                
                case ServiceLinkProtocol.ERROR_UNKNOWN_HOST:
                    // We couldn't find the machine. Assume its our own fault.
               //     failedOutgoingConnections++;
                    throw new NonFatalIOException(
                            new UnknownHostException("Failed to find host"
                                    + " within " + timeout + " ms."));      
                
                case ServiceLinkProtocol.ERROR_PORT_NOT_FOUND:
                    // User error
             //       failedOutgoingConnections++;
                    throw new ConnectException("Remote port not found!");
                    
                case ServiceLinkProtocol.ERROR_CONNECTION_REFUSED:
                    // User error
              //      failedOutgoingConnections++;
                    throw new ConnectException("Connection refused by server!");
                    
                case ServiceLinkProtocol.ERROR_ILLEGAL_TARGET:
                    // User error
             //       failedOutgoingConnections++;
                    throw new NonFatalIOException("Attempting to connect to " +
                            "illegal target! (" + target + ")");
                }
            }   
        } 
    }

    public boolean matchAdditionalRuntimeRequirements(Map<String, ?> requirements) {
        return true;
    }

    public void connect(DirectSocketAddress src, DirectSocketAddress srcHub, 
            int port, int remoteFragmentation, int remoteBufferSize, 
            int timeout, long index) {

        // Incoming connection...        
      //  incomingConnections++;
        
        // Get the serversocket (if it exists). 
        VirtualServerSocket ss = parent.getServerSocket(port);
        
        // Could not find it, so send a 'port not found' error back
        if (ss == null) { 
            logger.info("Failed find VirtualServerSocket(" + port + ")");
       //     rejectedIncomingConnections++;

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
                localFragmentation, localBufferSize, localMinimalACKSize, 
                remoteFragmentation, remoteBufferSize, sa, serviceLink, index, 
                null);
        
        sockets.put(index, s);
                
        int accept = ss.incomingConnection(s);
        
        if (accept != 0) { 
            sockets.remove(index);
        //    rejectedIncomingConnections++;
            
            if (accept == -1) { 
                serviceLink.nackVirtualConnection(index, 
                        ServiceLinkProtocol.ERROR_CONNECTION_REFUSED);
            } else { 
                serviceLink.nackVirtualConnection(index, 
                        ServiceLinkProtocol.ERROR_SERVER_OVERLOAD);
            }
        } 
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
            return;
        } 
        
        try {
            serviceLink.closeVirtualConnection(vc);
        } catch (Exception e) {
            logger.warn("Failed to forward close for virtual socket: " + vc, e); 
        }
    }

    public void connectACK(long index, int fragment, int buffer) {
        
        HubRoutedVirtualSocket s = sockets.get(index);
       
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
    
    public boolean gotMessage(long index, int len, DataInputStream in) 
        throws IOException {

        HubRoutedVirtualSocket s = sockets.get(index);
        
        // This can happen if we have just closed the socket.
        if (s == null) { 
            
            /*
            // Sanity check -- remove ASAP
            if (!closedSockets.contains(index)) { 
                logger.warn("BAD!! Got message for an unknown socket!: " + index 
                        + " size = " + data.length);
            } else { */
            
                logger.warn("BAD!! Got message for already closed socket!: " 
                        + index + " size = " + len);
            /*}*/
            return false;
        } 

        s.message(len, in);
        return true;
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

    public int getDefaultTimeout() {
        return DEFAULT_CONNECT_TIMEOUT;
    }

    
}
