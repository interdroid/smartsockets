package smartsockets.hub.servicelink;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.HubProtocol;


public class ServiceLink implements Runnable {
    
    // TODO: create different loggers here!
    protected static Logger logger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.servicelink");

    protected final HashMap callbacks = new HashMap();
    
    
    private static final int TIMEOUT = 5000;
    private static final int DEFAULT_WAIT_TIME = 10000;
    
    private static ServiceLink serviceLink;
    
    private final DirectSocketFactory factory;
    private final SocketAddressSet myAddress; 
    
    private boolean connected = false;
    
    private SocketAddressSet userSuppliedAddress;
    private SocketAddressSet hubAddress; 
    private DirectSocket hub;
    private DataOutputStream out; 
    private DataInputStream in; 
    
    private int nextCallbackID = 0;    
    
    private int maxWaitTime = DEFAULT_WAIT_TIME;
    
    private ServiceLink(SocketAddressSet hubAddress, 
            SocketAddressSet myAddress) throws IOException { 
        
        factory = DirectSocketFactory.getSocketFactory();
        
        this.userSuppliedAddress = hubAddress;
        this.myAddress = myAddress;              
               
        Thread t = new Thread(this, "ServiceLink Message Reader");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void register(String identifier, CallBack callback) { 

        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, callback);              
    }
    
    protected synchronized void registerCallback(String identifier, 
            SimpleCallBack cb) { 
        
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override callback " 
                    + identifier);
            return;
        }
        
        callbacks.put(identifier, cb);        
    }
    
    protected synchronized Object findCallback(String identifier) {         
        return callbacks.get(identifier);        
    }
    
    protected synchronized void removeCallback(String identifier) {         
        callbacks.remove(identifier);        
    }
    
    private synchronized void setConnected(boolean value) {
        connected = value;
        notifyAll();
    }
    
    private synchronized boolean getConnected() {
        return connected;
    }
    
    private synchronized boolean waitConnected(int time) {
        
        while (!connected) { 
            try { 
                wait(time);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        
        return connected;        
    }
            
    private void closeConnection() {
        setConnected(false);
        DirectSocketFactory.close(hub, out, in);                               
    }
    
    private void connectToHub(SocketAddressSet address) throws IOException { 
        try {
            // Create a connection to the hub
            hub = factory.createSocket(address, TIMEOUT, null);
            
            out = new DataOutputStream(hub.getOutputStream());
            in = new DataInputStream(hub.getInputStream());
                           
            // Ask if we are allowed to join
            out.write(HubProtocol.SERVICELINK_CONNECT);
            out.writeUTF(myAddress.toString());
            out.flush();
                
            // Get the result
            int reply = in.read();
        
            // Throw an exception if the hub refuses our conenction
            if (reply != HubProtocol.SERVICELINK_ACCEPTED) {
                throw new IOException("Hub denied connection request");                
            }
        
            // If the connection is accepted, the hub will give us its full 
            // address (since the user supplied one may be a partial).  
            hubAddress = new SocketAddressSet(in.readUTF());
            
            logger.info("Hub at " + address + " accepted connection, " +
                    "it's real address is: " + hubAddress);            

            hub.setSoTimeout(0);
            
            setConnected(true);
        } catch (IOException e) {            
            logger.warn("Connection setup to hub at " + address 
                    + " failed: ", e);            
            closeConnection();
            throw e;
        } 
    }
                           
    private void handleMessage() throws IOException { 
        
        String source = in.readUTF();        
        String sourceHub = in.readUTF();                
        String targetModule = in.readUTF();
        int opcode = in.readInt();
        String message = in.readUTF();
            
        logger.info("ServiceLink: Received message for " + targetModule);
                        
        CallBack target = (CallBack) findCallback(targetModule);
            
        if (target == null) { 
            logger.warn("ServiceLink: Callback " + targetModule + " not found");                                       
        } else { 
            SocketAddressSet src = new SocketAddressSet(source);
            SocketAddressSet srcHub = null;
            
            if (sourceHub != null && sourceHub.length() > 0) { 
                srcHub = new SocketAddressSet(sourceHub);
            }
            
            target.gotMessage(src, srcHub, opcode, message);
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
        
    public synchronized void send(SocketAddressSet target, 
            SocketAddressSet targetHub, String targetModule, int opcode, 
            String message) { 

        if (!connected) {
            logger.info("Cannot send message: not connected to hub");            
            return;
        }
                
        logger.info("Sending message to hub: [" + target.toString() + ", " +
                targetModule + ", " + opcode + ", " + message + "]");
        
        try { 
            out.write(ServiceLinkProtocol.MESSAGE);
            out.writeUTF(target.toString());
            
            if (targetHub != null) { 
                out.writeUTF(targetHub.toString());                   
            } else { 
                out.writeUTF("");
            }
            
            out.writeUTF(targetModule);
            out.writeInt(opcode);
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
        }        
    }
    
    private synchronized int getNextSimpleCallbackID() { 
        return nextCallbackID++;
    }
    
    public ClientInfo [] localClients() throws IOException {
        return clients(hubAddress, "");
    }
    
    public ClientInfo [] localClients(String tag) throws IOException {
        return clients(hubAddress, tag);
    }
    
    public ClientInfo [] clients(SocketAddressSet hub) throws IOException {
        return clients(hub, "");
    }
           
    private ClientInfo [] convertToClientInfo(String [] message) { 
        
        ClientInfo [] result = new ClientInfo[message.length];
        
        for (int i=0;i<message.length;i++) { 
            result[i] = new ClientInfo(message[i]);
        }
        
        return result;        
    }
    
    private  HubInfo [] convertToHubInfo(String [] message) { 
        
        HubInfo [] result = new HubInfo[message.length];
        
        for (int i=0;i<message.length;i++) { 
            result[i] = new HubInfo(message[i]);
        }
        
        return result;        
    }
    
    
    public ClientInfo [] clients(SocketAddressSet hub, String tag) throws IOException {

        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get clients: not connected to hub");            
            throw new IOException("No connection to hub!");
        }
                
        logger.info("Requesting client list from hub");
    
        String id = "GetClientsForHub" + getNextSimpleCallbackID();
    
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.CLIENTS_FOR_HUB);
                out.writeUTF(id);
                out.writeUTF(hub.toString());
                out.writeUTF(tag);                
                out.flush();            
            }
            
            return convertToClientInfo((String []) tmp.getReply());        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");            
        } finally { 
            removeCallback(id);
        }
    }
    
    public ClientInfo [] clients() throws IOException {
        return clients("");
    }

    public ClientInfo [] clients(String tag) throws IOException {        
        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get clients: not connected to hub");            
            throw new IOException("No connection to hub!");
        }
                
        logger.info("Requesting client list from hub");
    
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
            
            return convertToClientInfo((String []) tmp.getReply());        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");            
        } finally { 
            removeCallback(id);
        }
    }
   
    
    public SocketAddressSet [] hubs() throws IOException {
        
        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get list of hubs: not connected to hub");            
            throw new IOException("No connection to hub!");
        }        
        
        logger.info("Requesting hub list from hub");
        
        String id = "GetHubs" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.HUBS);
                out.writeUTF(id);
                out.flush();            
            } 
            
            String [] reply = (String []) tmp.getReply();        
            return SocketAddressSet.convertToSocketAddressSet(reply);        
                        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
    }

    public HubInfo [] hubDetails() throws IOException {
        
        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get list of hubs: not connected to hub");            
            throw new IOException("No connection to hub!");
        }        
        
        logger.info("Requesting hub details from hub");
        
        String id = "GetHubDetails" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.HUB_DETAILS);
                out.writeUTF(id);
                out.flush();            
            } 
            
            return convertToHubInfo((String []) tmp.getReply());
                        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
    }

    
    public SocketAddressSet [] locateClient(String client) throws IOException {

        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get direction to client: not connected to hub");            
            throw new IOException("No connection to hub!");
        }        
        
        logger.info("Requesting direction to client " + client + " from hub");
        
        String id = "GetDirection" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.DIRECTION);
                out.writeUTF(id);
                out.writeUTF(client);
                out.flush();            
            } 
            
            String [] reply = (String []) tmp.getReply();        
            return SocketAddressSet.convertToSocketAddressSet(reply);        
                        
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
    }

    
    
    public SocketAddressSet getAddress() throws IOException {
        
        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot get hub address: not connected to hub");            
            throw new IOException("No connection to hub!");
        }
        
        return hubAddress;
    }
    
    public boolean registerService(String tag, String info) throws IOException {

        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot register service: not connected to hub");            
            throw new IOException("No connection to hub!");
        }        
        
        logger.info("Requesting service registration: " + tag + " " + info); 
        
        String id = "RegisterService" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        registerCallback(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.REGISTER_SERVICE);
                out.writeUTF(id);
                out.writeUTF(tag);
                out.writeUTF(info);
                out.flush();            
            } 

            String [] reply = (String []) tmp.getReply();            
            return reply != null && reply.length == 1 && reply[0].equals("OK");
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
    }
    
   
    
    public static ServiceLink getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress) { 
                                       
        if (address == null) {  
            throw new NullPointerException("Hub address is null!");
        }
                
        if (myAddress == null) { 
            throw new NullPointerException("Local address is null!");
        }
        
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

    public SocketAddressSet findSharedHub(SocketAddressSet myMachine, 
            SocketAddressSet targetMachine) {
        
        if (!waitConnected(maxWaitTime)) {
            logger.info("Cannot find shared hub: not connected");            
            return null;
        }   

        // TODO DUMMY IMPLEMENTATION --- FIX!!!!!        
        return hubAddress;
    }
    
    public void run() {
        
        // Connect to the hub and processes the messages it gets. When the 
        // connection is lost, it will try to reconnect.         
        while (true) { 
            do {            
                try { 
                    if (hubAddress == null) {
                        connectToHub(userSuppliedAddress);
                    } else { 
                        connectToHub(hubAddress);
                    }
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
