package ibis.connect.gossipproxy.connections;

import ibis.connect.direct.DirectSocket;
import ibis.connect.gossipproxy.ProxyDescription;
import ibis.connect.gossipproxy.ProxyList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;

public abstract class MessageForwardingConnection extends BaseConnection {

    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
        super(s, in, out, connections, proxies);
    }
    
    private boolean forwardAnyMessage(boolean client, String proxy, String src, 
            String target, String module, int code, String message, 
            int hopsLeft) {

        BaseConnection c = connections.getConnection(proxy); 
        
        if (c != null && c instanceof ProxyConnection) {             
            ProxyConnection tmp = (ProxyConnection) c;            
            tmp.writeMessage(client, src, target, module, code, message, hopsLeft);
            return true;
        }   

        return false;
    }
    
    protected void forwardAnyMessage(boolean client, ProxyDescription p, String src, String target, 
            String module, int code, String message, int hopsLeft) {
        
        logger.info("Attempting to forward message to proxy "
                + p.proxyAddress);
        
        if (forwardAnyMessage(client, p.proxyAddressAsString, src, target, module, code, message, hopsLeft)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + p.proxyAddressAsString + " using direct link");
            return;            
        } 
         
        if (hopsLeft == 0) {
            logger.info("Failed to forward message to proxy " + p.proxyAddressAsString 
                    + " and we are not allowed to use an indirection!");
            return;
        } 
            
        logger.info("Failed to forward message to proxy " + p.proxyAddressAsString 
                + " using direct link, using indirection");

        // We don't have a direct connection, but we should be able to reach the
        // proxy indirectly
        ProxyDescription p2 = p.getIndirection();
        
        if (p2 == null) {
            // Oh dear, we don't have an indirection!
            logger.warn("Indirection address of " + p.proxyAddressAsString + " is null!");
            return;
        } 

        if (forwardAnyMessage(client, p2.proxyAddressAsString, src, target, module, code, message, hopsLeft)) { 
            logger.info("Succesfully forwarded message to proxy " 
                    + p2.proxyAddressAsString + " using direct link");
            return;            
        } 

        logger.info("Failed to forward message to proxy " + p.proxyAddressAsString 
                + " or it's indirection " + p2.proxyAddressAsString);
    }
       
    protected void forwardClientMessage(String src, String target, String module, 
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
                forwardAnyMessage(true, p, src, target, module, code, message, hopsLeft);
            }        
        } 
            
        logger.info("No more proxy available that know " + target);        
    } 
    
    protected void forwardClientMessage(String src, String target, String module, 
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
            forwardAnyMessage(true, p, src, target, module, code, message, p.getHops());
        }        
            
        logger.info("No more proxy available that know " + target); 
    }
}
