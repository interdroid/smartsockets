package smartsockets.proxy.connections;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Iterator;

import smartsockets.direct.DirectSocket;
import smartsockets.proxy.state.ProxyDescription;
import smartsockets.proxy.state.ProxyList;

public abstract class MessageForwardingConnection extends BaseConnection {

    protected MessageForwardingConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out, Connections connections, ProxyList proxies) {
        super(s, in, out, connections, proxies);
    }
    
    // Send a message over a proxy connection.
    private boolean forwardMessageToProxy(String proxy, String src, 
            String srcProxy, String target, String targetProxy,  
            String module, int code, String message, int hopsLeft) {

        BaseConnection c = connections.getConnection(proxy); 
        
        if (c != null && c instanceof ProxyConnection) {             
            ProxyConnection tmp = (ProxyConnection) c;            
            tmp.writeMessage(src, srcProxy, target, targetProxy, module, code, 
                    message, hopsLeft);
            return true;
        }   

        return false;
    }
    
    // Tries to forward a message to a given proxy, directly or indirectly.  
    private void forwardMessageToProxy(ProxyDescription p, String src, 
            String srcProxy, String target, String targetProxy,  
            String module, int code, String message, int hopsLeft) {
        
        String address = p.proxyAddressAsString;
        
        logger.info("Attempting to forward message to proxy "
                + p.proxyAddress);
        
        if (forwardMessageToProxy(address, src, srcProxy, target, targetProxy, 
                module, code, message, hopsLeft)) {
            
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

        if (forwardMessageToProxy(p2.proxyAddressAsString, src, srcProxy, 
                target, targetProxy, module, code, message, hopsLeft)) { 

            logger.info("Succesfully forwarded message to proxy " 
                    + p2.proxyAddressAsString + " using direct link");
            return;            
        } 

        logger.info("Failed to forward message to proxy " + address 
                + " or it's indirection " + p2.proxyAddressAsString);
    }
    
    // Forwards a message from a client to a target client. If the target is 
    // directly reachable the message is forwarded using the client connection. 
    // otherwise, if the target proxy is known it is forwarded to that proxy. 
    // If the target proxy is not known, it is forwarded to all proxies we know.
    protected void forwardMessageFromClient(String src, String target, 
            String targetProxy, String module, int code, String message) { 
        // Get the local proxy address.
        String local = knownProxies.getLocalDescription().proxyAddressAsString;
        
        // TODO: what is a decent value for hopsleft ??
        forward(src, local, target, targetProxy, module, code, message, 10);            
    }

    protected void forward(String src, String srcProxy, String target, 
            String targetProxy, String module, int code, String message, 
            int hopsLeft) {     
        
        // First check if we can find the target locally             
        BaseConnection c = (BaseConnection) connections.getConnection(target);
        
        if (c != null && c instanceof ClientConnection) {

            logger.info("Attempting to directly forward message to client " 
                    + target + "@" + targetProxy);
           
            // We found the target, so lets forward the message
            boolean result = ((ClientConnection) c).sendMessage(src, srcProxy, 
                    module, code, message);
            
            if (result) {
                logger.info("Directly forward message to client " 
                        + target + "@" + targetProxy + " succeeded!...");                
                return;
            }
        } 
        
        logger.info("Failed to directly forward message to " + target + "@" 
             + targetProxy + ": target not locally available, trying other proxies");

        // Lets see if we directly known the proxy that the client is 
        // associated with. 
        if (targetProxy != null && targetProxy.length() > 0) { 
            ProxyDescription p = knownProxies.get(targetProxy);
            
            if (p != null) { 
                forwardMessageToProxy(p, src, srcProxy, target, targetProxy, 
                        module, code, message, p.getHops());                
                
                logger.info("Directly forwarded message to proxy: " 
                        + targetProxy);
                
                return;
            }
        }
        
        // The target has no proxy address, or we don't know the proxy that he 
        // is associated with (which is a bit fishy, but it could be an old 
        // address or a user-typo), so we just broadcast the message to all
        // proxies we are conencted to and hope for the best.
        
        // First check if we are allowed to broadcast any further.... 
        if (--hopsLeft <= 0) { 
            return;
        }

        // Still some hops left, so we now broadcast the message.
        logger.info("Broadcasting message for " + target + "@" + targetProxy); 
                
        Iterator itt = knownProxies.connectedProxiesIterator();
        
        while (itt.hasNext()) { 
            String p = ((ProxyDescription) itt.next()).proxyAddressAsString;

            // Make sure we don't return the message the the sender...
            if (!p.equals(srcProxy)) { 
                forwardMessageToProxy(p, src, srcProxy, target, targetProxy, 
                        module, code, message, hopsLeft);
            }
        }        
            
        logger.info("Finished broadcasting message for " + target + "@" 
                + targetProxy);    
    }
    
}
