package smartsockets.virtual.modules;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;

public abstract class AbstractDirectModule extends MessagingModule {
    
   
    protected static final byte ACCEPT              = 1;
    protected static final byte PORT_NOT_FOUND      = 2;
    protected static final byte CONNECTION_REJECTED = 4;   
   
    protected DirectSocketFactory direct;  
    
    protected AbstractDirectModule(String name, boolean requiresServiceLink) { 
        super(name, requiresServiceLink);
    }
        
    protected abstract VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in); 
    
    protected void handleAccept(DirectSocket ds) {
        
        DataInputStream in = null;
        DataOutputStream out = null;
        
        incomingConnections++;
         
        if (logger.isDebugEnabled()) { 
            logger.debug(module + ": Got incoming connection on " + ds);
        }
           
        try {
            ds.setTcpNoDelay(true);
            
            in = new DataInputStream(ds.getInputStream());
            out = new DataOutputStream(ds.getOutputStream());
           
            
            
         //   String remote = in.readUTF();
            // int targetPort = in.readInt();
    
            int targetPort = ds.getUserData();
            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Target port " + targetPort);
            }
         
            // Next check if the port exists locally
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
            
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Connection seems OK, checking is " +
                        "server is willing to accept");
            }
            
            VirtualSocket vs = createVirtualSocket(
                    parent.getLocalVirtual(), // TODO: WRONG!!!
                   /* new VirtualSocketAddress(remote)*/
                            ds, out, in);
            
            // Next check if the serverSocket is willing to accept                        
            boolean accept = vss.incomingConnection(vs);
            
            if (!accept) {
                
                rejectedIncomingConnections++;
                
                out.write(CONNECTION_REJECTED);
                out.flush();                
                DirectSocketFactory.close(ds, out, in);
                
                //if (logger.isDebugEnabled()) { 
                    logger.warn(module + ": Connection failed, QUEUE FULL!");
               // }
                
                return;
            }
            
            acceptedIncomingConnections++;
            
        } catch (Exception e) {          
            failedIncomingConnections++;
            logger.warn(module + ": Got exception during connection setup!", e);            
            DirectSocketFactory.close(ds, out, in);
        }
    }
    
    protected VirtualSocket handleConnect(VirtualSocketAddress target, 
            DirectSocket s, int timeout, Map<String, Object> properties) throws IOException {
        
        VirtualSocket tmp = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try {
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            
            tmp = createVirtualSocket(target, s, out, in); 
            
        } catch (IOException e) {
            // This module worked fine, but we got a 'normal' exception while 
            // connecting (i.e., because the other side refused to connection). 
            // There is no use trying other modules.          
            failedOutgoingConnections++;
            DirectSocketFactory.close(s, out, in);
            throw e;
        }
                
        // Now wait until the other side agrees to the connection (may throw an
        // exception and close the socket if something is wrong) 
        tmp.waitForAccept();
        
        acceptedOutgoingConnections++;
        
        // Reset the timeout to the default value (infinite). 
        s.setSoTimeout(0);
        return tmp;        
    }
    
   
}
