package ibis.smartsockets.virtual.modules;


import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.virtual.VirtualServerSocket;
import ibis.smartsockets.virtual.VirtualSocket;
import ibis.smartsockets.virtual.VirtualSocketAddress;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.HashMap;


public abstract class AbstractDirectModule extends MessagingModule implements AcceptHandler {
    
    public static final byte ACCEPT              = 1;
    public static final byte PORT_NOT_FOUND      = 2;
    public static final byte CONNECTION_REJECTED = 4;   
    public static final byte SERVER_OVERLOAD     = 5;   
    
    protected DirectSocketFactory direct;  
    
    private HashMap<Integer, AcceptHandler> handlers = null;
    
    protected AbstractDirectModule(String name, boolean requiresServiceLink) { 
        super(name, requiresServiceLink);
    }
        
    public synchronized void installAcceptHandler(int port, AcceptHandler h) { 
        
        if (handlers == null) { 
            handlers = new HashMap<Integer, AcceptHandler>(1);
        }
        
        handlers.put(port, h);
    }
     
    // Find the accept handler for the given port. If no handler is found, 
    // the default handler (this object) will be returned. 
    private synchronized AcceptHandler findAcceptHandler(int targetPort) {
        
        if (handlers == null) { 
            return this;
        }
        
        AcceptHandler tmp = handlers.get(targetPort);
            
        if (tmp == null) { 
            return this;
        }
            
        return tmp;
    }
    
    protected abstract VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in); 
    
    
    public void accept(DirectSocket ds, int targetPort) { 
       
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try {
            out = new DataOutputStream(ds.getOutputStream());
            
            // Check if the port exists locally
            VirtualServerSocket vss = parent.getServerSocket(targetPort);
            
            if (vss == null) { 
                out.write(PORT_NOT_FOUND);
                out.flush();                
                DirectSocketFactory.close(ds, out, in);
                
                rejectedIncomingConnections++;
                
                if (logger.isDebugEnabled()) { 
                    logger.debug(module + ": Connection failed, PORT not found!");
                }                
                
                return;
            }
            
            in = new DataInputStream(ds.getInputStream());
            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Connection seems OK, checking is " +
                        "server is willing to accept");
            }
            
            VirtualSocket vs = createVirtualSocket(
                    parent.getLocalVirtual(), // TODO: WRONG!!!
                   /* new VirtualSocketAddress(remote)*/
                            ds, out, in);
            
            // Next check if the serverSocket is willing to accept                        
            int accept = vss.incomingConnection(vs);
            
            if (accept != 0) {
                
                rejectedIncomingConnections++;
                
                if (accept == -1) {                 
                    out.write(CONNECTION_REJECTED);
                } else { 
                    out.write(SERVER_OVERLOAD);
                }
                out.flush();                
                DirectSocketFactory.close(ds, out, in);
                
                if (logger.isInfoEnabled()) {
                    logger.info(module + ": Connection failed for port " 
                            + targetPort + ": " 
                            + (accept < 0 ? "REFUSED" : "OVERLOAD")) ;
                }
                
                return;                
            } 
            
            acceptedIncomingConnections++;
            
        } catch (Exception e) {          
            failedIncomingConnections++;
            logger.warn(module + ": Got exception during connection setup!", e);            
            DirectSocketFactory.close(ds, out, in);
        }
    }
    
    protected void handleAccept(DirectSocket ds) {
        incomingConnections++;
         
        if (logger.isDebugEnabled()) { 
            logger.debug(module + ": Got incoming connection on " + ds);
        }
           
        try {
            ds.setTcpNoDelay(true);
            
            int targetPort = ds.getUserData();
            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Target port " + targetPort);
            }
            
            findAcceptHandler(targetPort).accept(ds, targetPort);
            
        } catch (Exception e) {          
            logger.warn(module + ": Got exception during connection setup!", e);            
            DirectSocketFactory.close(ds, null, null);
        }
    }
}
