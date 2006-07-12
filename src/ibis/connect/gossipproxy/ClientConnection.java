package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ClientConnection extends BaseConnection {

    private final String clientAddress;
    
    ClientConnection(String clientAddress, DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
     
        super(s, in, out, connections, proxies);        
        this.clientAddress = clientAddress;
    }

    private boolean forwardMessage(String proxy, String target, String module, 
            int code, String message) {

        BaseConnection c = connections.getConnection(proxy); 
        
        if (c != null && c instanceof ProxyConnection) {             
            ProxyConnection tmp = (ProxyConnection) c;            
            tmp.writeMessage(clientAddress, target, module, code, message);
            return true;
        }   

        return false;
    }
    
    private void forwardMessage(ProxyDescription p, String target, 
            String module, int code, String message) {
        
        logger.info("Attempting to forward message to proxy "
                + p.proxyAddress);
        
        String proxy = p.proxyAddress.toString();
        
        if (forwardMessage(proxy, target, module, code, message)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + proxy + " using direct link");
            return;            
        } 
         
        logger.info("Failed to forward message to proxy " + proxy 
                + " using direct link, using indirection");
        
        // We don't have a direct connection, but we should be able to reach the
        // proxy indirectly
        SocketAddressSet addr = p.getIndirection();
            
        if (addr == null) {
            // Oh dear, we don't have an indirection!
            logger.warn("Indirection address of " + proxy + " is null!");
            return;
        } 
        
        String proxy2 = addr.toString();
        
        if (forwardMessage(proxy2, target, module, code, message)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + proxy2 + " using direct link");
            return;            
        } 

        logger.info("Failed to forward message to proxy " + proxy 
                + " or it's indirection " + proxy2);
    }
    
    private void handleMessage() throws IOException { 
        // Read the message
        String target = in.readUTF();                    
        String module = in.readUTF();
        int code = in.readInt();
        String message = in.readUTF();
        
        if (logger.isDebugEnabled()) { 
            logger.debug("Incoming message: [" + target + ", " 
                    + module + ", " + code + ", " + message); 
        } 

        // First check if we can find the target locally             
        BaseConnection c = (BaseConnection) connections.getConnection(target);
        
        if (c != null && c instanceof ClientConnection) {
            // We found the target, so lets forward the message
            boolean result = ((ClientConnection) c).sendMessage(clientAddress, 
                    module, code, message);
            return;
        } 
        
        logger.info("Failed to directly forward message to " + target + 
            ": target not locally know, trying other proxies");

        Iterator itt = knownProxies.findProxiesForTarget(target, false);
        
        while (itt.hasNext()) { 
            ProxyDescription p = (ProxyDescription) itt.next();
            forwardMessage(p, target, module, code, message);
        }
    } 
                
    private void disconnect() { 
        connections.removeConnection(clientAddress);
        DirectSocketFactory.close(s, out, in);            
    } 
    
    synchronized boolean sendMessage(String src, String module, int code, 
            String message) {  
        
        try{ 
            out.write(ServiceLinkProtocol.MESSAGE);
            out.writeUTF(src);
            out.writeUTF(module);
            out.writeInt(code);
            out.writeUTF(message);
            out.flush();
            return true;
        } catch (IOException e) {
            logger.warn("Connection " + src + " is broken!", e);
            DirectSocketFactory.close(s, out, in);
            return false;                
        }
    }
        
    protected String getName() {
        return "ServiceLink(" + clientAddress + ")";
    }

    protected boolean runConnection() {           
                     
        try { 
            int opcode = in.read();

            switch (opcode) { 
            case ServiceLinkProtocol.MESSAGE:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " got message");
                }                     
                handleMessage();
                return true;
                
            case ServiceLinkProtocol.DISCONNECT:
                if (logger.isDebugEnabled()) {
                    logger.debug("Connection " + clientAddress + " disconnecting");
                } 
                disconnect();
                return false;
                
            default:
                logger.warn("Connection " + clientAddress + " got unknown opcode" 
                        + opcode + " -- disconnecting");
                disconnect();
                return false;                
            } 
            
        } catch (Exception e) { 
            logger.warn("Connection to " + clientAddress + " is broken!", e);
            DirectSocketFactory.close(s, out, in);            
        }
        
        return false;
    }
}
