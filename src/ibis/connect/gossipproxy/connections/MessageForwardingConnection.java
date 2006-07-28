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
    
    // Send a message over a proxy connection.
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
    
    // Tries to forward a message to a given proxy, directly or indirectly.  
    private void forwardMessage(ProxyDescription p, String src, String target, 
            String module, int code, String message, int hopsLeft) {
        
        String address = p.proxyAddressAsString;
        
        logger.info("Attempting to forward message to proxy "
                + p.proxyAddress);
        
        if (forwardMessage(address, src, target, module, code, 
                message, hopsLeft)) {
            
            logger.info("Succesfully forwarded message to proxy " 
                    + address + " using direct link");
            return;            
        } 
         
        if (hopsLeft == 0) {
            logger.info("Failed to forward message to proxy " 
                    + address + " and we are not allowed to use" 
                    +" an indirection!");
            return;
        } 
            
        logger.info("Failed to forward message to proxy " 
                + address + " using direct link, " 
                + "trying indirection");

        // We don't have a direct connection, but we should be able to reach the
        // proxy indirectly
        ProxyDescription p2 = p.getIndirection();
        
        if (p2 == null) {
            // Oh dear, we don't have an indirection!
            logger.warn("Indirection address of " + address + " is null!");
            return;
        } 

        if (forwardMessage(p2.proxyAddressAsString, src, target, module, code, 
                message, hopsLeft)) { 

            logger.info("Succesfully forwarded message to proxy " 
                    + p2.proxyAddressAsString + " using direct link");
            return;            
        } 

        logger.info("Failed to forward message to proxy " + address 
                + " or it's indirection " + p2.proxyAddressAsString);
    }
       
    // Forwards a message to a certain target client. If the target is 
    // directly reachable the message is forwarded using the client connection. 
    // otherwise, it is forwarded to all proxies that claim to know the client.
    // If hopsleft reaches 0, the forwarding will stop.
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

    // Forwards a message to a certain target client. If the target is 
    // directly reachable the message is forwarded using the client connection. 
    // otherwise, it is forwarded to all proxies that claim to know the client.
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
