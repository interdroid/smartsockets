package smartsockets.virtual.modules.direct;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import smartsockets.Properties;
import smartsockets.direct.DirectServerSocket;
import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.ModuleNotSuitableException;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.modules.AbstractDirectModule;

public class Direct extends AbstractDirectModule {
    

    protected static final byte ACCEPT              = 1;
    protected static final byte PORT_NOT_FOUND      = 2;
    protected static final byte CONNECTION_REJECTED = 4;   
           
    private DirectSocketFactory direct;   
    private AcceptThread acceptThread;
    private DirectServerSocket server; 
    
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
        
    public Direct() {
        super("ConnectModule(Direct)", false);
    } 
    
    public void initModule(TypedProperties properties) throws Exception {     

        // Retrieve the value of the port property (if set). Default value 
        // is '0' (any available port).
        int port = 0;
        
        if (properties != null) { 
            System.err.println("PROPERTIES!!!");
            port = properties.getIntProperty(Properties.DIRECT_PORT, 0);
            System.err.println("PORT IS " + port);
        } else { 
            System.err.println("NO PROPERTIES!!!");
        }
        
        // Create a direct socket factory.
        direct = DirectSocketFactory.getSocketFactory();
        
        // TODO: why the default ??
        TypedProperties p = Properties.getDefaultProperties();
        
        int backlog = p.getIntProperty(Properties.DIRECT_BACKLOG);
                
        // Create a server socket to accept incoming connections. 
        HashMap prop = new HashMap();
        prop.put("PortForwarding", "yes");
        prop.put("ForwardingMayFail", "yes");
        prop.put("SameExternalPort", "no");
                
        try {            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Creating ServerSocket on port " + port);
            }
                        
            server = direct.createServerSocket(port, backlog, prop);

            if (logger.isInfoEnabled()) {
                logger.info(module + ": ServerSocket created: "
                        + server.getAddressSet());
            }
            
        } catch (IOException e) {            
            logger.warn(module + ": Failed to create ServerSocket on port " 
                    + port, e);
            throw e;
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
            
    public SocketAddressSet getAddresses() { 
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
            Map properties) throws ModuleNotSuitableException, IOException {

        outgoingConnectionAttempts++;
        
        DirectSocket s = null;

        try { 
            s = direct.createSocket(target.machine(), timeout, properties, 
                    target.port());
        } catch (IOException e) {
            // Failed to create the connection, but other modules may be more 
            // succesful.            
            throw new ModuleNotSuitableException(module + ": Failed to " +
                    "connect to " + target + " " + e);           
        }

        return handleConnect(target, s, timeout, properties);
        
        /*
        DirectVirtualSocket tmp = null;
        
        try { 
            s.setSoTimeout(timeout);
            
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
                        
            tmp = new DirectVirtualSocket(target, s, out, in, properties);
                                                     
            out.writeUTF(target.machine().toString());
            out.writeInt(target.port());
            out.flush();                                
        } catch (IOException e) {
            // This module worked fine, but we got a 'normal' exception while 
            // connecting (i.e., because the other side refused to connection). 
            // There is no use trying other modules.            
            DirectSocketFactory.close(s, out, in);
            throw e;
        }
                
        // Now wait until the other side agrees to the connection (may throw an
        // exceptionand close the socket if something is wrong) 
        tmp.waitForAccept();
        
        // Reset the timeout to the default value (infinite). 
        s.setSoTimeout(0);
        
        return tmp;
        */
    }

    public boolean matchAdditionalRuntimeRequirements(Map requirements) {
        // No additional properties, so always matches requirements.
        return true;
    }

    protected VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in) {        
        return new DirectVirtualSocket(a, s, out, in, null); 
    }   
}