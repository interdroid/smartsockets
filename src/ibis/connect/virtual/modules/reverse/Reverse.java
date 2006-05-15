package ibis.connect.virtual.modules.reverse;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.virtual.ModuleNotSuitableException;
import ibis.connect.virtual.VirtualServerSocket;
import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.modules.ConnectModule;
import ibis.connect.virtual.modules.direct.Direct;
import ibis.connect.virtual.modules.direct.DirectVirtualSocket;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Map;

public class Reverse extends ConnectModule {
    
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
    }

    public void initModule() throws Exception {
        // nothing do do here.
    }

    public void startModule() throws Exception {
        if (serviceLink == null) {
            throw new Exception(name + ": no service link available!");       
        }
        
        direct = (Direct) parent.findModule("ConnectModule(Direct)");
        
        if (direct == null) {
            throw new Exception(name + ": no direct module available!");       
        }        
    }

    
    public SocketAddressSet getAddresses() {       
        // TODO Auto-generated method stub
        return null;
    }
        
    public VirtualSocket connect(VirtualSocketAddress target, int timeout,
            Map properties) throws ModuleNotSuitableException, IOException {

        // When the reverse module is asked for a connection to a remote 
        // address, it simply creates a local serversocket and send a message 
        // to the remote machine asking for a connection. If no connection comes 
        // in within the specified timeout, the module assumes the connection 
        // setup has failed and throws an exception. If a connection does come 
        // in, the local socket still has to wait for the remote serversocket to 
        // do an accept.         
                
        if (timeout == 0 || timeout > DEFAULT_TIMEOUT) { 
            timeout = DEFAULT_TIMEOUT; 
        }
                        
        VirtualServerSocket ss = 
            (VirtualServerSocket) parent.createServerSocket(0, 1, null);
                       
        serviceLink.send(target.machine(), name, PLEASE_CONNECT, 
                target.port() + ":" + ss.getPort());
        
        DirectVirtualSocket s = null;
        
        try { 
            ss.setSoTimeout(timeout);            
            // TODO: Check we connected to the right machine here ? 
            s = (DirectVirtualSocket) ss.accept();
        } catch (Exception e) {
            throw new ModuleNotSuitableException(name + ": Failed to set up " +
                    "reverse connection"); 
        } finally { 
            ss.close();
        }
        
        // Now wait for the remote accept to finish 
        s.waitForAccept();      
        return s;
    }
    
    void setupConnection(VirtualServerSocket ss, VirtualSocketAddress target) {       
        try { 
            DirectVirtualSocket s = 
                (DirectVirtualSocket) direct.connect(target, DEFAULT_TIMEOUT, null);
            
            logger.info(name + ": Connection to " + target + " created!");                
            
            // NOTE: The socket is now accepted by the temporary serversocket 
            // we created on the other side. Now we must check if the server 
            // socket on our side is also willing to accept it.
                        
            if (!ss.incomingConnection(s)) { 
                // TODO: send reply ??
                logger.info(name + ": ServerSocket refused " + target);
                s.connectionRejected();
            }
        } catch (Exception e) {
            logger.info(name + ": Connection to " + target + " failed!", e);                
        }
    }        
    
    public void gotMessage(SocketAddressSet src, int opcode, String message) {

        if (opcode != PLEASE_CONNECT) { 
            logger.warn(name + ": got unexpected message from " + src 
                    + " opcode = " + opcode + ", message = " + message);
            return;
        }
    
        logger.info(name + ": handling connection request from " + src 
                + " message = \"" + message + "\"");  
        
        int localport = 0;
        int remoteport = 0;
        
        try {
            int index = message.indexOf(':');
            localport = Integer.parseInt(message.substring(0, index));
            remoteport = Integer.parseInt(message.substring(index+1));            
        } catch (Exception e) {
            logger.warn(name + ": failed to parse target port!", e);
            return;
        }
        
        VirtualServerSocket ss = parent.getServerSocket(localport);
        
        if (ss == null) {
            // TODO: send reply ??
            logger.info(name + ": port " + localport + " not found!");
            return;            
        }

        VirtualSocketAddress target = new VirtualSocketAddress(src, remoteport);
        
        if (USE_THREAD) { 
            new Connector(ss, target).start();    
        } else { 
            setupConnection(ss, target);
        }
    }
}
