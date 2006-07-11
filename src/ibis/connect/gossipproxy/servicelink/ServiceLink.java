package ibis.connect.gossipproxy.servicelink;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import ibis.connect.gossipproxy.ProxyProtocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class ServiceLink {
    
    private static final int TIMEOUT = 5000;
    
    private static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLink.class.getName());
    
    private static ServiceLink serviceLink;
        
    private final DirectSocketFactory factory;
    private final SocketAddressSet myAddress; 
        
    private DirectSocket proxy;               
    private DataOutputStream out; 
    private DataInputStream in; 
    
    private final HashMap callbacks = new HashMap(); 
    
    private class Reader extends Thread { 
        
        Reader() { 
            super("ServiceLink Message Reader");
            setDaemon(true);
        }
        
        public void run() { 
            receiveMessages();                
        }        
    }
          
    private ServiceLink(SocketAddressSet proxy, SocketAddressSet myAddress) 
        throws IOException { 
        
        factory = DirectSocketFactory.getSocketFactory();
        this.myAddress = myAddress;
                
        connectToProxy(proxy);
        
        new Reader().start();       
    }
        
    private void connectToProxy(SocketAddressSet address) throws IOException { 
        try {            
            proxy = factory.createSocket(address, TIMEOUT, null);
            
            out = new DataOutputStream(proxy.getOutputStream());
            in = new DataInputStream(proxy.getInputStream());
                           
            out.write(ProxyProtocol.PROXY_SERVICELINK_CONNECT);
            out.writeUTF(myAddress.toString());        
            out.flush();
                
            int reply = in.read();
        
            if (reply != ProxyProtocol.REPLY_SERVICELINK_ACCEPTED) {
                throw new IOException("Proxy denied connection request");                
            }
        
            logger.info("Proxy at " + address + " accepted connection");            

            proxy.setSoTimeout(0);
            
        } catch (IOException e) {
            
            logger.warn("Connection setup to proxy at " + address 
                    + " failed: ", e);
            
            DirectSocketFactory.close(proxy, out, in);                    
            throw e;
        } 
    }
       
    public synchronized void register(String identifier, MessageCallback callback) { 
    
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, callback);        
    }
    
    private synchronized MessageCallback getCallBack(String identifier) {         
        return (MessageCallback) callbacks.get(identifier);        
    }
        
    void receiveMessages() { 
        
        while (true) { 
         
            try { 
                int header = in.read();
                
                if (header != ServiceLinkProtocol.MESSAGE) {
                    logger.warn("ServiceLink: Received unknown opcode!");
                    break;
                } 
                    
                String source = in.readUTF();
                String targetID = in.readUTF();
                int opcode = in.readInt();
                String message = in.readUTF();
                    
                logger.info("ServiceLink: Received message for " + targetID);
                                
                MessageCallback target = getCallBack(targetID);
                    
                if (target == null) { 
                    logger.warn("ServiceLink: Callback " + targetID 
                            + " not found");                                       
                } else { 
                    SocketAddressSet src = new SocketAddressSet(source);
                    target.gotMessage(src, opcode, message);
                } 
                    
            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while receiving!", e);
                // TODO: some form of fault tolerance ??            
            }               
        }               
    }
        
    public synchronized void send(SocketAddressSet target, String targetModule, 
            int opcode, String message) { 

        logger.info("Sending message to proxy: [" + target.toString() + ", " +
                targetModule + ", " + opcode + ", " + message + "]");
        
        try { 
            out.write(ServiceLinkProtocol.MESSAGE);
            out.writeUTF(target.toString());
            out.writeUTF(targetModule);
            out.writeInt(opcode);
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            // TODO: some form of fault tolerance ??            
        }        
    }
           
    public static ServiceLink getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress) { 
        
        if (serviceLink == null) {
            try { 
                serviceLink = new ServiceLink(address, myAddress);                 
            } catch (Exception e) {
                logger.warn("ServiceLink: Failed to connect to proxy!", e);
                return null;
            }                        
        }
        
        return serviceLink;
    }
}
