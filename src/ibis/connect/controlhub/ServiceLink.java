package ibis.connect.controlhub;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class ServiceLink implements Protocol {
    
    private static final int TIMEOUT = 5000;
    
    private static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLink.class.getName());
    
    private static ServiceLink serviceLink;
        
    private final DirectSocketFactory directFactory;
    private final SocketAddressSet myAddress; 
        
    private DirectSocket hub;               
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
          
    private ServiceLink(SocketAddressSet hub, SocketAddressSet myAddress) 
        throws IOException { 
        
        directFactory = DirectSocketFactory.getSocketFactory();
        this.myAddress = myAddress;
                
        connectToHub(hub);
        
        new Reader().start();       
    }
        
    private void connectToHub(SocketAddressSet address) throws IOException { 
        try {            
            hub = directFactory.createSocket(address, TIMEOUT, null);
            
            out = new DataOutputStream(hub.getOutputStream());
            in = new DataInputStream(hub.getInputStream());
                           
            out.write(CONNECT);
            out.writeUTF(myAddress.toString());        
            out.flush();
                
            int reply = in.read();
        
            if (reply != CONNECT_ACCEPTED) {
                throw new IOException("ControlHub denied connection request");                
            }
        
            logger.info("Hub at " + address + " accepted connection");            

            hub.setSoTimeout(0);
            
        } catch (IOException e) {
            
            logger.warn("Connection setup to control hub at " + address 
                    + " failed: ", e);
            
            DirectSocketFactory.close(hub, out, in);                    
            throw e;
        } 
    }
       
    public synchronized void register(String identifier, CallBack callback) { 
    
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, callback);        
    }
    
    private synchronized CallBack getCallBack(String identifier) {         
        return (CallBack) callbacks.get(identifier);        
    }
        
    void receiveMessages() { 
        
        while (true) { 
         
            try { 
                int header = in.read();
                
                if (header != MESSAGE) {
                    logger.warn("ServiceLink: Received unknown opcode!");
                    break;
                } 
                    
                String source = in.readUTF();
                String targetID = in.readUTF();
                int opcode = in.readInt();
                String message = in.readUTF();
                    
                logger.info("ServiceLink: Received message for " + targetID);
                                
                CallBack target = getCallBack(targetID);
                    
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

        logger.info("Sending message to hub: [" + target.toString() + ", " +
                targetModule + ", " + opcode + ", " + message + "]");
        
        try { 
            out.write(MESSAGE);
            out.writeUTF(target.toString());
            out.writeUTF(targetModule);
            out.writeInt(opcode);
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            // TODO: some form of fault tolerance ??            
        }        
    }
    
    public static ServiceLink getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress) { 
        
        if (serviceLink == null) {
            try { 
                serviceLink = new ServiceLink(address, myAddress);                 
            } catch (Exception e) {
                logger.warn("ServiceLink: Failed to connect to hub!", e);
                return null;
            }                        
        }
        
        return serviceLink;
    }
}
