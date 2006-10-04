package ibis.connect.controlhub;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import ibis.connect.virtual.VirtualSocketAddress;
import ibis.connect.virtual.service.CallBack;
import ibis.connect.virtual.service.Client;
import ibis.connect.virtual.service.ServiceLink;

public class ServiceLinkImpl extends ServiceLink {
    
    private static final int TIMEOUT = 5000;
    
    private static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLinkImpl.class.getName());
    
    private static ServiceLinkImpl serviceLink;
        
    private final DirectSocketFactory directFactory;
    private final SocketAddressSet hubAddress;
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
          
    private ServiceLinkImpl(SocketAddressSet hub, SocketAddressSet myAddress) 
        throws IOException { 
        
        this.hubAddress = hub;
        this.myAddress = myAddress;
        
        directFactory = DirectSocketFactory.getSocketFactory();
                
        connectToHub(hub);
        
        new Reader().start();       
    }
        
    private void connectToHub(SocketAddressSet address) throws IOException { 
        try {            
            hub = directFactory.createSocket(address, TIMEOUT, null);
            
            out = new DataOutputStream(hub.getOutputStream());
            in = new DataInputStream(hub.getInputStream());
                           
            out.write(Protocol.CONNECT);
            out.writeUTF(myAddress.toString());        
            out.flush();
                
            int reply = in.read();
        
            if (reply != Protocol.CONNECT_ACCEPTED) {
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
                
                if (header != Protocol.MESSAGE) {
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
                    target.gotMessage(src, hubAddress, opcode, message);
                } 
                    
            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while receiving!", e);
                // TODO: some form of fault tolerance ??            
            }               
        }               
    }
        
    public synchronized void send(SocketAddressSet target, 
            SocketAddressSet targetProxy, String targetModule, 
            int opcode, String message) { 

        logger.info("Sending message to hub: [" + target.toString() + ", " +
                targetModule + ", " + opcode + ", " + message + "]");
        
        try { 
            out.write(Protocol.MESSAGE);
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
    
    public static ServiceLinkImpl getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress) { 
        
        if (serviceLink == null) {
            try { 
                serviceLink = new ServiceLinkImpl(address, myAddress);                 
            } catch (Exception e) {
                logger.warn("ServiceLink: Failed to connect to hub!", e);
                return null;
            }                        
        }
        
        return serviceLink;
    }
       
    public SocketAddressSet[] proxies() throws IOException {
        return new SocketAddressSet [] { hubAddress }; 
    }

    public SocketAddressSet getAddress() {
        return hubAddress;
    }

    public SocketAddressSet[] locateClient(String client) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public Client [] clients() throws IOException {
        return clients("");
    }
    
    public Client [] clients(String tag) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public Client [] clients(SocketAddressSet proxy) throws IOException {
        return clients(proxy, "");
    }
    
    public Client [] clients(SocketAddressSet proxy, String tag) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public Client [] localClients() throws IOException {
        return clients(hubAddress, "");
    }
    
    public Client [] localClients(String tag) throws IOException {
        return clients(hubAddress, tag);
    }

    public boolean registerService(String tag, VirtualSocketAddress address) throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public SocketAddressSet findSharedProxy(SocketAddressSet myMachine, SocketAddressSet targetMachine) {
        return myAddress;
    }
}
