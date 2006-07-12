package ibis.connect.gossipproxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.SocketAddressSet;

public abstract class MessageForwardingConnection extends BaseConnection {

    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
        super(s, in, out, connections, proxies);
    }
    
    private boolean forwardMessage(String proxy, String src, String target, 
            String module, int code, String message, int hopsLeft) {

        BaseConnection c = connections.getConnection(proxy); 
        
        if (c != null && c instanceof ProxyConnection) {             
            ProxyConnection tmp = (ProxyConnection) c;            
            tmp.writeMessage(src, target, module, code, message, hopsLeft);
            return true;
        }   

        return false;
    }
    
    private void forwardMessage(ProxyDescription p, String src, String target, 
            String module, int code, String message, int hopsLeft) {
        
        logger.info("Attempting to forward message to proxy "
                + p.proxyAddress);
        
        String proxy = p.proxyAddress.toString();
        
        if (forwardMessage(proxy, src, target, module, code, message, hopsLeft)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + proxy + " using direct link");
            return;            
        } 
         
        if (hopsLeft == 0) {
            logger.info("Failed to forward message to proxy " + proxy 
                    + " and we are not allowed to use an indirection!");
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

        if (forwardMessage(proxy2, src, target, module, code, message, hopsLeft)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + proxy2 + " using direct link");
            return;            
        } 

        logger.info("Failed to forward message to proxy " + proxy 
                + " or it's indirection " + proxy2);
    }
    
    protected void forwardMessage(String src, String target, String module, 
            int code, String message, int hopsLeft) {     
    
        // First check if we can find the target locally             
        BaseConnection c = (BaseConnection) connections.getConnection(target);
        
        if (c != null && c instanceof ClientConnection) {
            // We found the target, so lets forward the message
            boolean result = ((ClientConnection) c).sendMessage(src, module, 
                    code, message);
            return;
        } 
        
        logger.info("Failed to directly forward message to " + target + 
            ": target not locally know, trying other proxies");

        if (hopsLeft > 0) {

            hopsLeft--;

            Iterator itt = knownProxies.findProxiesForTarget(target, false);
        
            while (itt.hasNext()) { 
                ProxyDescription p = (ProxyDescription) itt.next();
                forwardMessage(p, src, target, module, code, message, hopsLeft);
            }        
        } 
            
        logger.info("No more proxy available that know " + target);        
    } 
    
    protected void forwardMessage(String src, String target, String module, 
            int code, String message) {
        
        // First check if we can find the target locally             
        BaseConnection c = (BaseConnection) connections.getConnection(target);
        
        if (c != null && c instanceof ClientConnection) {
            // We found the target, so lets forward the message
            boolean result = ((ClientConnection) c).sendMessage(src, module, 
                    code, message);
            return;
        } 
        
        logger.info("Failed to directly forward message to " + target + 
            ": target not locally know, trying other proxies");

        Iterator itt = knownProxies.findProxiesForTarget(target, false);
            
        while (itt.hasNext()) { 
            ProxyDescription p = (ProxyDescription) itt.next();
            forwardMessage(p, src, target, module, code, message, p.getHops());
        }        
            
        logger.info("No more proxy available that know " + target); 
    }
}
