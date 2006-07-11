package ibis.connect.gossipproxy.servicelink;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.gossipproxy.ProxyList;
import ibis.connect.gossipproxy.ProxyProtocol;

public class ServiceLinkHandler {

    private static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLinkHandler.class.getName());
    
    private class Connection extends Thread { 
        
        private final String src;
        private final DirectSocket s;               
        private final DataOutputStream out; 
        private final DataInputStream in;

        private boolean done = false;
        
        Connection(String src, DirectSocket s, DataOutputStream out,
                DataInputStream in) {
            this.src = src;
            this.s = s;
            this.out = out;
            this.in = in;
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
            Connection c = getConnection(target);
            
            if (c != null) {
                // We found the target, so lets forward the message
                boolean result = c.sendMessage(src, module, code, message);                
                return;
            } 
            
            logger.info("Failed to directly forward message to " + target + 
                ": target not locally know, trying other proxies");

            if (knownProxies.sendMessage(src, target, module, code, message)) {
                logger.info("Forwarded message for " + target);                 
            } else { 
                logger.info("Failed to forward message to " + target + 
                    ": no proxies where found that know the target!");
            }                                     
        } 
                
        private void disconnect() { 
            done = true;
            removeConnection(src);
            DirectSocketFactory.close(s, out, in);            
        } 
    
        private void receive() {            
            try { 
                int opcode = in.read();

                switch (opcode) { 
                case ServiceLinkProtocol.MESSAGE:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Connection " + src + " got message");
                    }                     
                    handleMessage();
                    break;
                case ServiceLinkProtocol.DISCONNECT:
                    if (logger.isDebugEnabled()) {
                        logger.debug("Connection " + src + " disconnecting");
                    } 
                    disconnect();
                    break;
                default:
                    logger.warn("Connection " + src + " got unknown opcode" 
                            + opcode + " -- disconnecting");
                    disconnect();
                } 
            } catch (Exception e) { 
                logger.warn("Connection to " + src + " is broken!", e);
                DirectSocketFactory.close(s, out, in);
            }
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
        
        public void run() {            
            while (!done) { 
                receive();
            }                                    
        }
    } 
    
    private final HashMap connections = new HashMap(); 
    
    private final ProxyList knownProxies; 
    
    public ServiceLinkHandler(ProxyList knownProxies) { 
        this.knownProxies = knownProxies;
    }
    
    private synchronized void addConnection(String key, Connection c) { 
        connections.put(key, c);
    }

    private synchronized Connection getConnection(String key) { 
        return (Connection) connections.get(key);
    }

    private synchronized void removeConnection(String key) { 
        connections.remove(key);
    }
    
    public boolean handleConnection(DirectSocket s, DataInputStream in, 
            DataOutputStream out) throws IOException {

        try { 
            String src = in.readUTF();
                
            if (getConnection(src) != null) { 
                if (logger.isDebugEnabled()) { 
                    logger.debug("Incoming connection from " + src + 
                            " refused, since it already exists!"); 
                } 
            
                out.write(ProxyProtocol.REPLY_SERVICELINK_REFUSED);
                out.flush();
                DirectSocketFactory.close(s, out, in);
                return false;
            }
        
            if (logger.isDebugEnabled()) { 
                logger.debug("Incoming connection from " + src 
                        + " accepted"); 
            } 

            out.write(ProxyProtocol.REPLY_SERVICELINK_ACCEPTED);
            out.flush();
            
            Connection c = new Connection(src, s, out, in);
            addConnection(src, c);                                               
            c.start();
     
            return true;
            
        } catch (IOException e) { 
            logger.warn("Got exception while handling connect!", e);
            DirectSocketFactory.close(s, out, in);
        }  
        
        return false;
    }       
}
