package smartsockets.virtual.modules.reverse;

import java.util.Map;

import smartsockets.direct.DirectSocketAddress;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.modules.AbstractDirectModule;
import smartsockets.virtual.modules.MessagingModule;
import smartsockets.virtual.modules.direct.Direct;
import smartsockets.virtual.modules.direct.DirectVirtualSocket;

public class Reverse extends MessagingModule {
    
    private static final int DEFAULT_TIMEOUT = 4000;            
    private static final boolean USE_THREAD = true; 
    
    private static final int PLEASE_CONNECT = 1; 
    
    private Direct direct;    
    
    private class Connector extends Thread { 
        
        private final VirtualServerSocket ss;
        private final VirtualSocketAddress target;
        
        Connector(VirtualServerSocket ss, VirtualSocketAddress target) {
            this.ss = ss;
            this.target = target;
        }
        
        public void run() {
            setupConnection(ss, target);
        }
    }
    
    public Reverse() {
        super("ConnectModule(Reverse)", true);
        properties.put("direct.forcePublic", "");
    }

    public void initModule(TypedProperties properties) throws Exception {
        // nothing do do here.
    }

    public void startModule() throws Exception {
        if (serviceLink == null) {
            throw new Exception(module + ": no service link available!");       
        }
        
        direct = (Direct) parent.findModule("ConnectModule(Direct)");
        
        if (direct == null) {
            throw new Exception(module + ": no direct module available!");       
        }        
    }

    
    public DirectSocketAddress getAddresses() {       
        // Nothing to do here....
        return null;
    }
    
    public VirtualSocket connect(VirtualSocketAddress target, int timeout,
            Map<String, Object> properties) throws ModuleNotSuitableException {
       
        // When the reverse module is asked for a connection to a remote 
        // address, it simply creates a local serversocket and send a message 
        // to the remote machine asking for a connection. If no connection comes 
        // in within the specified timeout, the module assumes the connection 
        // setup has failed and throws an exception. If a connection does come 
        // in, the local socket still has to wait for the remote serversocket to 
        // do an accept.         
            
        // First check if we are trying to connect to ourselves (which makes no 
        // sense for this module... 
        if (target.machine().sameMachine(parent.getLocalHost())) { 
            throw new ModuleNotSuitableException(module + ": Cannot set up " +
                "a connection to myself!"); 
        }
                
        if (timeout == 0) { 
            timeout = DEFAULT_TIMEOUT; 
        }
        
        VirtualServerSocket ss = null;
        
        try { 
            ss = (VirtualServerSocket) parent.createServerSocket(0, 1, null);
        } catch (Exception e) {
            // All exceptions are converted into a module not suitable 
            // exception. 
            throw new ModuleNotSuitableException(module + ": Failed to set up " +
                    "reverse connection", e); 
        } 
        
        byte [][] message = new byte[5][];
        
        VirtualSocketAddress vs = ss.getLocalSocketAddress();
        
        message[0] = fromInt(target.port());
        message[1] = fromSocketAddressSet(vs.machine());
        message[2] = fromInt(vs.port());
        message[3] = fromSocketAddressSet(vs.hub());
        message[4] = fromString(vs.cluster());        
        
        serviceLink.send(target.machine(), target.hub(), module, PLEASE_CONNECT, 
                message);
        
        DirectVirtualSocket s = null;
        
        try { 
            ss.setSoTimeout(timeout);            
            s = (DirectVirtualSocket) ss.accept();
        } catch (Exception e) {
            // All exceptions are converted into a module not suitable 
            // exception. 
            throw new ModuleNotSuitableException(module + ": Failed to set up " +
                    "reverse connection", e); 
        } finally { 
            try { 
                ss.close();
            } catch (Exception e) {
                // ignored
            }
        }
        
        // The rest of the connection setup is handled by the generic code in 
        // the virtual socket factory. 
        return s;
    }
    
    void setupConnection(VirtualServerSocket ss, VirtualSocketAddress target) {       
        try { 
            DirectVirtualSocket s = 
                (DirectVirtualSocket) direct.connect(target, DEFAULT_TIMEOUT, 
                        properties);
            
            if (logger.isInfoEnabled()) {
                logger.info(module + ": Connection to " + target + " created!");                
            }
            
            // NOTE: The socket is now accepted by the temporary serversocket 
            // we created on the other side. Now we must check if the server 
            // socket on our side is also willing to accept it.
                        
            int accept = ss.incomingConnection(s);
            
            if (accept != 0) {
                
                if (accept == -1) { 
                    s.connectionRejected(AbstractDirectModule.CONNECTION_REJECTED);
                } else { 
                    s.connectionRejected(AbstractDirectModule.SERVER_OVERLOAD);
                }
                
                if (logger.isInfoEnabled()) {
                    logger.info(module + ": ServerSocket refused " + target);
                }
            }
        } catch (Exception e) {
            if (logger.isInfoEnabled()) {
                logger.info(module + ": Connection to " + target + " failed!", e);
            }
        }
    }        
        
    public void gotMessage(DirectSocketAddress src, DirectSocketAddress srcProxy, 
            int opcode, byte [][] message) {

        if (opcode != PLEASE_CONNECT) { 
            logger.warn(module + ": got unexpected message from " + src + "@" + 
                   srcProxy + " opcode = " + opcode + ", message = " + message);
            return;
        }
        
        if (logger.isInfoEnabled()) {
            logger.info(module + ": handling connection request from " + src + "@" + 
                    srcProxy + " message = \"" + message + "\"");
        }
        
        int localport = 0;
        VirtualSocketAddress target = null;
        
        try {
            localport                = toInt(message[0]);                        
            DirectSocketAddress machine = toSocketAddressSet(message[1]);            
            int port                 = toInt(message[2]); 
            DirectSocketAddress hub     = toSocketAddressSet(message[3]);                        
            String cluster           = toString(message[4]);
            
            target = new VirtualSocketAddress(machine, port, hub, cluster);            
        } catch (Exception e) {
            logger.warn(module + ": failed to parse target address!", e);
            return;
        }
        
        VirtualServerSocket ss = parent.getServerSocket(localport);
        
        if (ss == null) {
            // TODO: send reply ??
            if (logger.isInfoEnabled()) {
                logger.info(module + ": port " + localport + " not found!");
            }
            return;            
        }
        
        if (USE_THREAD) { 
            new Connector(ss, target).start();    
        } else { 
            setupConnection(ss, target);
        }
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        // Nothing to check here ? 
        return true;
    }
}
