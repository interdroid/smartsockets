package ibis.smartsockets.virtual.modules.direct;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectServerSocket;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.NonFatalIOException;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.modules.AbstractDirectModule;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Direct extends AbstractDirectModule {
    
    private static int DEFAULT_CONNECT_TIMEOUT = 3000;
    
    private final DirectSocketFactory direct;   
    private AcceptThread acceptThread;
    private DirectServerSocket server; 
    
    private int defaultSendBuffer;
    private int defaultReceiveBuffer;
        
    private class AcceptThread extends Thread { 
        
        AcceptThread() { 
            super("DirectModule AcceptThread");
            setDaemon(true);
        }
                     
        public void run() {         
            while (true) {
                handleAccept();                
            }
        }
    }
        
    public Direct(DirectSocketFactory direct) {
        super("ConnectModule(Direct)", false);
        
        // Store the direct socket factory for later use
        this.direct = direct;
    } 
    
    public void initModule(TypedProperties properties) throws Exception {
        
        // Retrieve the value of the port property (if set). Default value 
        // is '0' (any available port).
        int port = 0;
        
        if (properties != null) { 
            port = properties.getIntProperty(
                    SmartSocketsProperties.DIRECT_PORT, 0);        
        }
        
        // TODO: why the default ??
        TypedProperties p = SmartSocketsProperties.getDefaultProperties();
        
        int backlog = p.getIntProperty(SmartSocketsProperties.DIRECT_BACKLOG, 100);
        
        defaultReceiveBuffer = p.getIntProperty(
                SmartSocketsProperties.DIRECT_RECEIVE_BUFFER, -1);
        
        defaultSendBuffer = p.getIntProperty(
                SmartSocketsProperties.DIRECT_SEND_BUFFER, -1);
                
        // Create a server socket to accept incoming connections. 
        HashMap <String, String> prop = new HashMap<String, String>(3);
        prop.put("PortForwarding", "yes");
        prop.put("ForwardingMayFail", "yes");
        prop.put("SameExternalPort", "no");
                
        try {            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Creating ServerSocket on port " + port);
            }
                        
            server = direct.createServerSocket(port, backlog, 
                    defaultReceiveBuffer, prop);

            if (logger.isInfoEnabled()) {
                logger.info(module + ": ServerSocket created: "
                        + server.getAddressSet());
            }
            
        } catch (IOException e) {            
            logger.info(module + ": Failed to create ServerSocket on port " 
                    + port, e);
            
            throw new Exception("Failed to create ServerSocket on port "  
                    + port + " (" + e.getMessage() + ")", e);            
        }
        
        if (logger.isInfoEnabled()) {
            logger.info(module + ": Starting AcceptThread");
        }

        // Finally start a thread to handle the incoming connections.
        acceptThread = new AcceptThread();
        acceptThread.start();
    }
    
    public void startModule() throws Exception {
        // nothing to do here...
    }
            
    public DirectSocketAddress getAddresses() { 
        return server.getAddressSet();
    }
    
    /*
    private boolean checkTarget(SocketAddressSet target) {  
        // TODO: implement
        return true;
    }
    
    private void handleSocket(DirectSocket s) {
        
        DataInputStream in = null;
        DataOutputStream out = null;
        
        if (logger.isDebugEnabled()) { 
            logger.debug(name + ": Got incoming connection on " + s);
        }
           
        try {         
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
        
            SocketAddressSet target = new SocketAddressSet(in.readUTF());            
            int targetPort = in.readInt();
    
            if (logger.isDebugEnabled()) { 
                logger.debug(name + ": Target port " + targetPort);
            }
                    
            // First check if we are the desired target machine...
            if (!checkTarget(target)) { 
                out.write(WRONG_MACHINE);
                out.flush();                
                DirectSocketFactory.close(s, out, in);
                
                if (logger.isDebugEnabled()) { 
                    logger.debug(name + ": Connection failed, WRONG machine!");
                }
                
                return;
            }
            
            // Next check if the port exists locally
            VirtualServerSocket vss = parent.getServerSocket(targetPort);
            
            if (vss == null) { 
                out.write(PORT_NOT_FOUND);
                out.flush();                
                DirectSocketFactory.close(s, out, in);
                
                if (logger.isDebugEnabled()) { 
                    logger.debug(name + ": Connection failed, PORT not found!");
                }                
                
                return;
            }
            
            if (logger.isDebugEnabled()) { 
                logger.debug(name + ": Connection seems OK, checking is " +
                        "server is willing to accept");
            }
            
            // Next check if the serverSocket is willing to accept
            DirectVirtualSocket dvs = new DirectVirtualSocket(
                    new VirtualSocketAddress(target, targetPort), s, out, in, null); 
                        
            boolean accept = vss.incomingConnection(dvs);
            
            if (!accept) {
                out.write(CONNECTION_REJECTED);
                out.flush();                
                DirectSocketFactory.close(s, out, in);
                
                if (logger.isDebugEnabled()) { 
                    logger.debug(name + ": Connection failed, REJECTED!");
                }
                
                return;
            }
            
        } catch (Exception e) {            
            logger.warn(name + ": Got exception during connection setup!", e);            
            DirectSocketFactory.close(s, out, in);
        }
    }
    */
    
    void handleAccept() {    
        try { 
            handleAccept(server.accept());    
        } catch (IOException e) {
            logger.warn(module + ": Got exception while waiting " +
                    "for connection!", e);
        }                
    }
                
    public VirtualSocket connect(VirtualSocketAddress target, int timeout,
            Map<String, Object> properties) throws NonFatalIOException {

        outgoingConnectionAttempts++;
        
        int sendBuffer = defaultSendBuffer;
        int receiveBuffer = defaultReceiveBuffer;
                
        if (properties != null) {
            
            Integer tmp = (Integer) properties.get("sendbuffer");
        
            if (tmp != null) { 
                sendBuffer = tmp;
            } 
        
            tmp = (Integer) properties.get("receivebuffer");
            
            if (tmp != null) { 
                receiveBuffer = tmp;
            } 
        }   
        
        try { 
            DirectSocket s = direct.createSocket(target.machine(), timeout, 0, 
                    sendBuffer, receiveBuffer, properties, false, 
                    target.port());
            
            // Next, we wrap the direct socket in a virtual socket and return it.
            // Any exceptions thrown here are forwarded to the user. Note that  
            // the connection setup is not complete yet, but the rest of it is  
            // in generic code.
            return createVirtualSocket(target, s);  
        } catch (IOException e) {
            // Failed to create the connection, but other modules may be more 
            // succesful.            
            throw new NonFatalIOException(e);           
        }
    }
    
    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        // No additional properties, so always matches requirements.
        return true;
    }

    protected VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in) { 
        return new DirectVirtualSocket(a, s, out, in, null);        
    }
    
    private VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s) throws IOException {
        
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try {
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());    
            return new DirectVirtualSocket(a, s, out, in, null);     
        } catch (IOException e) {
            // This module worked fine, but we got a 'normal' exception while 
            // connecting (i.e., because the other side refused to connection). 
            // There is no use trying other modules.          
            failedOutgoingConnections++;
            DirectSocketFactory.close(s, out, in);
            throw e;
        }
    }

    @Override
    public int getDefaultTimeout() {
        return DEFAULT_CONNECT_TIMEOUT;
    }   
}