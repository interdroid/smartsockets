package ibis.connect.gossipproxy;

import ibis.connect.direct.DirectSocket;
import ibis.connect.direct.DirectSocketFactory;
import ibis.connect.direct.SocketAddressSet;

import ibis.connect.virtual.service.ServiceLink;
import ibis.connect.virtual.service.CallBack;
import ibis.connect.virtual.service.SimpleCallBack;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.log4j.Logger;

public class ServiceLinkImpl extends ServiceLink implements Runnable {
    
    private static final int TIMEOUT = 5000;
    
    private static Logger logger = 
        ibis.util.GetLogger.getLogger(ServiceLinkImpl.class.getName());
    
    private static ServiceLinkImpl serviceLink;
        
    private final DirectSocketFactory factory;
    private final SocketAddressSet myAddress; 
    private final String tag;
    
    private boolean connected = false;
    
    private SocketAddressSet proxyAddress; 
    private DirectSocket proxy;
    private DataOutputStream out; 
    private DataInputStream in; 
    
    private int nextCallbackID = 0;    
       
   
    
    private ServiceLinkImpl(SocketAddressSet proxyAddress, 
            SocketAddressSet myAddress, String tag) throws IOException { 
        
        factory = DirectSocketFactory.getSocketFactory();
        
        this.proxyAddress = proxyAddress;
        this.myAddress = myAddress;              
        this.tag = tag;
               
        Thread t = new Thread(this, "ServiceLink Message Reader");
        t.setDaemon(true);
        t.start();
    }
               
    private synchronized void setConnected(boolean value) {
        connected = value;
    }
    
    private synchronized boolean getConnected() {
        return connected;
    }
    
    private void closeConnection() {
        setConnected(false);
        DirectSocketFactory.close(proxy, out, in);                               
    }
    
    private void connectToProxy(SocketAddressSet address) throws IOException { 
        try {            
            proxy = factory.createSocket(address, TIMEOUT, null);
            
            out = new DataOutputStream(proxy.getOutputStream());
            in = new DataInputStream(proxy.getInputStream());
                           
            out.write(ProxyProtocol.PROXY_SERVICELINK_CONNECT);
            out.writeUTF(myAddress.toString());
            out.writeUTF(tag);
            out.flush();
                
            int reply = in.read();
        
            if (reply != ProxyProtocol.REPLY_SERVICELINK_ACCEPTED) {
                throw new IOException("Proxy denied connection request");                
            }
        
            logger.info("Proxy at " + address + " accepted connection");            

            proxy.setSoTimeout(0);
            
            setConnected(true);
        } catch (IOException e) {            
            logger.warn("Connection setup to proxy at " + address 
                    + " failed: ", e);            
            closeConnection();
            throw e;
        } 
    }
              
    
         
    private void handleMessage() throws IOException { 
        
        String source = in.readUTF();
        String targetID = in.readUTF();
        int opcode = in.readInt();
        String message = in.readUTF();
            
        logger.info("ServiceLink: Received message for " + targetID);
                        
        CallBack target = (CallBack) findCallback(targetID);
            
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
        
        logger.info("ServiceLink: Received info for " + targetID + ". " 
                + " Receiving " + count + " strings....");
        
        String [] info = new String[count];
        
        for (int i=0;i<count;i++) { 
            info[i] = in.readUTF();
            logger.info(i + ": " + info[i]);
        }        

        logger.info("done receiving info");
        
        SimpleCallBack target = (SimpleCallBack) findCallback(targetID);
            
        if (target == null) { 
            logger.warn("ServiceLink: Callback " + targetID + " not found");                                       
        } else {             
            target.storeReply(info);
        } 
    }
    
    void receiveMessages() { 
                        
        while (getConnected()) { 
         
            try { 
                int header = in.read();
                
                switch (header) { 
                case -1: 
                    closeConnection();
                    break;
                
                case ServiceLinkProtocol.MESSAGE:
                    handleMessage();
                    break;
                    
                case ServiceLinkProtocol.INFO:
                    handleInfo();
                    break;
                    
                default:                     
                    logger.warn("ServiceLink: Received unknown opcode!");
                    closeConnection();                
                    break;                    
                }
                                                                   
            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while receiving!", e);
                closeConnection();
            }               
        }               
    }
        
    public synchronized void send(SocketAddressSet target, String targetModule, 
            int opcode, String message) { 

        if (!connected) {
            logger.info("Cannot send message: not connected to proxy");            
            return;
        }
                
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
            closeConnection();
        }        
    }
    
    private synchronized int getNextSimpleCallbackID() { 
        return nextCallbackID++;
    }
    
    public String [] localClients() throws IOException {
        return clients(proxyAddress, "");
    }
    
    public String [] localClients(String tag) throws IOException {
        return clients(proxyAddress, tag);
    }
    
    public String[] clients(SocketAddressSet proxy) throws IOException {
        return clients(proxy, "");
    }
    
    public String[] clients(SocketAddressSet proxy, String tag) throws IOException {

        if (!getConnected()) {
            logger.info("Cannot get clients: not connected to proxy");            
            throw new IOException("No connection to proxy!");
        }
                
        logger.info("Requesting client list from proxy");
    
        String id = "GetClientsForProxy" + getNextSimpleCallbackID();
    
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.CLIENTS_FOR_PROXY);
                out.writeUTF(id);
                out.writeUTF(proxy.toString());
                out.writeUTF(tag);                
                out.flush();            
            }
            
            return (String []) tmp.getReply();        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            closeConnection();
            throw new IOException("Connection to proxy lost!");            
        } finally { 
            removeCallback(id);
        }
    }
    
    public String [] clients() throws IOException {
        return clients("");
    }

    public String[] clients(String tag) throws IOException {        
        if (!getConnected()) {
            logger.info("Cannot get clients: not connected to proxy");            
            throw new IOException("No connection to proxy!");
        }
                
        logger.info("Requesting client list from proxy");
    
        String id = "GetAllClients" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.ALL_CLIENTS);
                out.writeUTF(id);
                out.writeUTF(tag);
                out.flush();            
            }
            
            return (String []) tmp.getReply();        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            closeConnection();
            throw new IOException("Connection to proxy lost!");            
        } finally { 
            removeCallback(id);
        }
    }
   
    
    public SocketAddressSet [] proxies() throws IOException {
        
        if (!getConnected()) {
            logger.info("Cannot get list of proxies: not connected to proxy");            
            throw new IOException("No connection to proxy!");
        }        
        
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
            
            String [] reply = (String []) tmp.getReply();        
            return SocketAddressSet.convertToSocketAddressSet(reply);        
                        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            closeConnection();
            throw new IOException("Connection to proxy lost!");
        } finally { 
            removeCallback(id);
        }
    }
    
    public SocketAddressSet [] directionToClient(String client, String tag) throws IOException {

        if (!getConnected()) {
            logger.info("Cannot get direction to client: not connected to proxy");            
            throw new IOException("No connection to proxy!");
        }        
        
        logger.info("Requesting direction to client " + client + "#" + tag 
                + " from proxy");
        
        String id = "GetDirection" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.DIRECTION);
                out.writeUTF(id);
                out.writeUTF(client);
                out.writeUTF(tag);
                out.flush();            
            } 
            
            String [] reply = (String []) tmp.getReply();        
            return SocketAddressSet.convertToSocketAddressSet(reply);        
                        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to proxy!", e);
            closeConnection();
            throw new IOException("Connection to proxy lost!");
        } finally { 
            removeCallback(id);
        }
    }


    public SocketAddressSet getAddress() {
        return proxyAddress;
    }
    
    public static ServiceLink getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress, String tag) { 
        
        if (serviceLink == null) {
            try { 
                serviceLink = new ServiceLinkImpl(address, myAddress, tag);                 
            } catch (Exception e) {
                logger.warn("ServiceLink: Failed to connect to proxy!", e);
                return null;
            }                        
        }
        
        return serviceLink;
    }

    public void run() {
        
        // Connect to the proxy and processes the messages it gets. When the 
        // connection is lost, it will try to reconnect.         
        while (true) { 
            do {            
                try { 
                    connectToProxy(proxyAddress);
                } catch (IOException e) {
                    try { 
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        // ignore
                    }
                }
            } while (!connected);
            
            receiveMessages();
        }
        
    }
}
