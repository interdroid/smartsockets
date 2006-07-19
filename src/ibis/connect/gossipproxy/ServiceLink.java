package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
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
    
    private int nextCallbackID = 0;    
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
    
    private class SimpleCallBack { 
        
        private boolean haveResult = false;
        private Object result; 
        
        synchronized Object getReply() {
            
            while (!haveResult) { 
                try { 
                    wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            
            return result;
        } 
            
        synchronized void storeReply(Object result) {            
            this.result = result;
            haveResult = true;
            notifyAll();
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
       
    public synchronized void registerCallback(String identifier, MessageCallback cb) { 
    
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, cb);        
    }
    
    private synchronized void registerCallback(String identifier, SimpleCallBack cb) { 
        
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, cb);        
    }
        
    private synchronized Object findCallback(String identifier) {         
        return callbacks.get(identifier);        
    }
    
    public synchronized void removeCallback(String identifier) {         
        callbacks.remove(identifier);        
    }
        
    private void handleMessage() throws IOException { 
        
        String source = in.readUTF();
        String targetID = in.readUTF();
        int opcode = in.readInt();
        String message = in.readUTF();
            
        logger.info("ServiceLink: Received message for " + targetID);
                        
        MessageCallback target = (MessageCallback) findCallback(targetID);
            
        if (target == null) { 
            logger.warn("ServiceLink: Callback " + targetID + " not found");                                       
        } else { 
            SocketAddressSet src = new SocketAddressSet(source);
            target.gotMessage(src, opcode, message);
        } 
    }

    private void handleInfo() throws IOException { 
        
        String targetID = in.readUTF();        
        int count = in.readInt();        
        
        logger.info("ServiceLink: Received info for " + targetID);
        
        String [] info = new String[count];
        
        for (int i=0;i<count;i++) { 
            info[i] = in.readUTF();
        }        
                        
        SimpleCallBack target = (SimpleCallBack) findCallback(targetID);
            
        if (target == null) { 
            logger.warn("ServiceLink: Callback " + targetID + " not found");                                       
        } else {             
            target.storeReply(info);
        } 
    }
    
    void receiveMessages() { 
        
        while (true) { 
         
            try { 
                int header = in.read();
                
                switch (header) { 
                case ServiceLinkProtocol.MESSAGE:
                    handleMessage();
                    break;
                    
                case ServiceLinkProtocol.INFO:
                    handleInfo();
                    break;
                    
                default:                     
                    logger.warn("ServiceLink: Received unknown opcode!");
                    break;                    
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
    
    private synchronized int getNextSimpleCallbackID() { 
        return nextCallbackID++;
    }
                 
    public String [] clients() {
        
        logger.info("Requesting client list from proxy");
    
        String id = "GetAllClients" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.CLIENTS);
                out.writeUTF(id);
                out.flush();            
            } 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            // TODO: some form of fault tolerance ??            
            return null;
        } finally { 
            removeCallback(id);
        }

        return (String []) tmp.getReply();        
    }
    
    public String [] localClients() {

        logger.info("Requesting local client list from proxy");
        
        String id = "GetLocalClients" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.LOCAL_CLIENTS);
                out.writeUTF(id);
                out.flush();            
            } 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            // TODO: some form of fault tolerance ??            
            return null;
        } finally { 
            removeCallback(id);
        }

        return (String []) tmp.getReply();        
    }
    
    public synchronized SocketAddressSet [] proxies() throws UnknownHostException {
        
        logger.info("Requesting proxy list from proxy");
        
        String id = "GetProxies" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.PROXIES);
                out.writeUTF(id);
                out.flush();            
            } 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            // TODO: some form of fault tolerance ??            
            return null;
        } finally { 
            removeCallback(id);
        }

        String [] reply = (String []) tmp.getReply();        
        return SocketAddressSet.convertToSocketAddressSet(reply);        
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
