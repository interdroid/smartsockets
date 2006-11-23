package smartsockets.virtual.modules;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.virtual.VirtualServerSocket;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;

public abstract class AbstractDirectModule extends ConnectModule {
    
    protected static final byte ACCEPT              = 1;
    protected static final byte PORT_NOT_FOUND      = 2;
  //  protected static final byte WRONG_MACHINE       = 3;     
    protected static final byte CONNECTION_REJECTED = 4;   
    
    protected DirectSocketFactory direct;  
    
    protected AbstractDirectModule(String name, boolean requiresServiceLink) { 
        super(name, requiresServiceLink);
    }
        
    private boolean checkTarget(SocketAddressSet target) {  
        // TODO: implement
        return true;
    }
        
    protected abstract VirtualSocket createVirtualSocket(VirtualSocketAddress a, 
            DirectSocket s, DataOutputStream out, DataInputStream in); 
    
    protected void handleAccept(DirectSocket ds) {
        
        DataInputStream in = null;
        DataOutputStream out = null;
        
        if (logger.isDebugEnabled()) { 
            logger.debug(module + ": Got incoming connection on " + ds);
        }
           
        try {         
            in = new DataInputStream(ds.getInputStream());
            out = new DataOutputStream(ds.getOutputStream());
           
            String remote = in.readUTF();
            int targetPort = in.readInt();
    
            if (logger.isDebugEnabled()) { 
                logger.debug(module + ": Target port " + targetPort);
            }
         
            // Next check if the port exists locally
            VirtualServerSocket vss = parent.getServerSocket(targetPort);
            
            if (vss == null) { 
                out.write(PORT_NOT_FOUND);
                out.flush();                
                DirectSocketFactory.close(ds, out, in);
                
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
                    new VirtualSocketAddress(remote), 
                            ds, out, in);
            
            // Next check if the serverSocket is willing to accept                        
            boolean accept = vss.incomingConnection(vs);
            
            if (!accept) {
                out.write(CONNECTION_REJECTED);
                out.flush();                
                DirectSocketFactory.close(ds, out, in);
                
                if (logger.isDebugEnabled()) { 
                    logger.debug(module + ": Connection failed, REJECTED!");
                }
                
                return;
            }
            
        } catch (Exception e) {            
            logger.warn(module + ": Got exception during connection setup!", e);            
            DirectSocketFactory.close(ds, out, in);
        }
    }
    
    protected VirtualSocket handleConnect(VirtualSocketAddress target, 
            DirectSocket s, int timeout, Map properties) throws IOException {
        
        VirtualSocket tmp = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        
        try { 
            s.setSoTimeout(timeout);
            
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
                
            out.writeUTF(parent.getVirtualAddressAsString()); 
            out.writeInt(target.port());
            out.flush();                
            
            tmp = createVirtualSocket(target, s, out, in); 
            
        } catch (IOException e) {
            // This module worked fine, but we got a 'normal' exception while 
            // connecting (i.e., because the other side refused to connection). 
            // There is no use trying other modules.            
            DirectSocketFactory.close(s, out, in);
            throw e;
        }
                
        // Now wait until the other side agrees to the connection (may throw an
        // exception and close the socket if something is wrong) 
        tmp.waitForAccept();
        
        // Reset the timeout to the default value (infinite). 
        s.setSoTimeout(0);
        
        return tmp;        
    }
}
