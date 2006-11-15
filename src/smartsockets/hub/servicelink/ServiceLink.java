package smartsockets.hub.servicelink;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.Logger;

import smartsockets.direct.DirectSocket;
import smartsockets.direct.DirectSocketFactory;
import smartsockets.direct.SocketAddressSet;
import smartsockets.hub.HubProtocol;
import smartsockets.hub.connections.VirtualConnection;
import smartsockets.hub.connections.VirtualConnections;

public class ServiceLink implements Runnable {
    
    // TODO: create different loggers here!
    private static final Logger logger = 
        ibis.util.GetLogger.getLogger("smartsockets.hub.servicelink");

    private static final int TIMEOUT = 5000;
    private static final int DEFAULT_WAIT_TIME = 10000;
           
    private final HashMap<String, Object> callbacks= 
        new HashMap<String, Object>();
           
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
    
    private final VirtualConnections vcs = new VirtualConnections();
    
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
            
            System.err.println("AAP ServiceLink: refusing to override callback " 
                    + identifier);
            
            new Exception().printStackTrace(System.err);
            
            return;
        }
        
        callbacks.put(identifier, callback);              
    }
    
    protected synchronized void register(String identifier, 
            SimpleCallBack cb) { 
        
        if (callbacks.containsKey(identifier)) { 
            logger.warn("ServiceLink: refusing to override simple callback " 
                    + identifier);
            
            System.err.println("NOOT ServiceLink: refusing to override simple callback " 
                    + identifier);
            
            new Exception().printStackTrace(System.err);
                        
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
    
    private synchronized void waitConnected(int time) throws IOException {
        
        while (!connected) { 
            try { 
                wait(time);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        
        if (!connected) { 
            throw new IOException("No connection to hub!");
        }
    }
            
    private void closeConnection() {
        
        // TODO: Close all virtual connections here ??
        
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
            
            if (logger.isInfoEnabled()) { 
                logger.info("Hub at " + address + " accepted connection, " +
                        "it's real address is: " + hubAddress);
            }

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
            
        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received message for " + targetModule);
        }
            
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
        
        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received info for " + targetID + ". " 
                    + " Receiving " + count + " strings....");
        }
        
        String [] info = new String[count];
        
        for (int i=0;i<count;i++) { 
            info[i] = in.readUTF();
            if (logger.isInfoEnabled()) {
                logger.info(i + ": " + info[i]);
            }
        }        

        if (logger.isInfoEnabled()) {
            logger.info("done receiving info");
        }
        
        SimpleCallBack target = (SimpleCallBack) findCallback(targetID);
            
        if (target == null) { 
            logger.warn("ServiceLink: Callback " + targetID + " not found");                                       
        } else {             
            target.storeReply(info);
        } 
    }
    
    private void handleIncomingConnection() throws IOException { 

        String id = in.readUTF();
        String source = in.readUTF();
        String info = in.readUTF();
        int timeout = in.readInt();
                
        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received request for incoming connection "
                    + "from " + source + " (" + id + ")"); 
        }
       
        VirtualConnectionCallBack cb = null;
        
        try {         
            cb = (VirtualConnectionCallBack) findCallback("__virtual");
        } catch (ClassCastException e) {
            // ignore            
        }
            
        if (cb == null) {         
            try {
                synchronized (this) {         
                    out.write(ServiceLinkProtocol.REPLY_VIRTUAL);
                    out.writeUTF(id);
                    out.writeUTF("DENIED");                    
                    out.writeUTF("No one will accept the connection");                
                    out.flush();            
                }
            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while writing to hub!", e);
                closeConnection();
                throw new IOException("Connection to hub lost!");            
            }
            
            return;
        } 

        VirtualConnection vc = vcs.newVC();
        
        if (!cb.connect(new SocketAddressSet(source), info, timeout, vc.number, id)) {            
            // Connection refused....            
            try {
                synchronized (this) {         
                    out.write(ServiceLinkProtocol.REPLY_VIRTUAL);
                    out.writeUTF(id);
                    out.writeUTF("DENIED");
                    out.writeUTF("Connection refused");
                    out.flush();
                    
                    vcs.freeVC(vc);
                }
            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while writing to hub!", e);
                closeConnection();
                throw new IOException("Connection to hub lost!");            
            }
        } 
        
        // Connection is now pending in the backlog of a serversocket somewhere.
        // It may be accepted or rejected at any time. 
    } 

    public void rejectIncomingConnection(int index, String id) {
        
        // The incoming connection 'index' was rejected.         
        VirtualConnection vc = vcs.getVC(index);
        vcs.freeVC(vc);
        
        try { 
            synchronized (this) {         
                out.write(ServiceLinkProtocol.REPLY_VIRTUAL);
                out.writeUTF(id);
                out.writeUTF("DENIED");  
                out.writeUTF("Connection refused");
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();                      
        }
    }
    
    public boolean acceptIncomingConnection(int index, String id) throws IOException { 
        
        // The incoming connection 'index' was accepted. The only problem is 
        // that we are not sure if we are in time. We therefore send a reply, 
        // for which we get a reply back again...            
        String localID = "ReplyVirtual" + getNextSimpleCallbackID();
            
        SimpleCallBack tmp = new SimpleCallBack();        
        register(localID, tmp);

        VirtualConnection vc = vcs.getVC(index);
        
        try { 
            synchronized (this) {         
                out.write(ServiceLinkProtocol.REPLY_VIRTUAL);
                out.writeUTF(id);
                out.writeUTF("OK");  
                out.writeInt(index);
                out.writeUTF(localID);                    
                out.flush();

                // TODO: fix credits here ?
                vc.init(100);                    
            }
            
            String [] reply = (String []) tmp.getReply();
            
            if (reply == null || reply.length != 1 || !reply[0].equals("OK")) { 
                // We accepted the connection, but the other side refused. This 
                // is most likely to be caused a timeout. All we have to do is 
                // inform the user and free the connection.
                
                VirtualConnectionCallBack cb = null;
                
                try {         
                    cb = (VirtualConnectionCallBack) findCallback("__virtual");
                } catch (ClassCastException e) {
                    // ignore            
                }
                    
                if (cb != null) {  
                    cb.disconnect(vc.number);
                } 
                  
                vcs.freeVC(vc);                
                return false;
            } 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");            
        } finally { 
            removeCallback(id);
        }
                
        return true;
    }
       
    private void handleIncomingClose() throws IOException { 

        int index = in.readInt();
        
        VirtualConnection vc = vcs.getVC(index);        
        vcs.freeVC(vc);
        
        VirtualConnectionCallBack cb = null;
        
        try {         
            cb = (VirtualConnectionCallBack) findCallback("__virtual");
        } catch (ClassCastException e) {
            // ignore            
        }

        if (cb != null) { 
            cb.disconnect(index);
        }
    }        

    private void handleIncomingMessage() throws IOException { 

        int index = in.readInt();
        int len = in.readInt();
        
        // TODO: optimize!
        byte [] data = new byte[len];
        
        in.read(data);
        
        VirtualConnectionCallBack cb = null;
        
        try {         
            cb = (VirtualConnectionCallBack) findCallback("__virtual");
        } catch (ClassCastException e) {
            // ignore            
        }

        if (cb != null) { 
            cb.gotMessage(index, data);
        }
    }        

    private void handleIncomingAck() throws IOException { 

        int index = in.readInt();
        
        VirtualConnection vc = vcs.getVC(index);
            
        if (vc == null) { 
            logger.warn("Virtual connection: " + index + " does not exist!");            
            throw new IOException("Not connected");
        }
            
        vc.addCredit();                  
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
                
                case ServiceLinkProtocol.CREATE_VIRTUAL:
                    handleIncomingConnection();
                    break;
                
                case ServiceLinkProtocol.CLOSE_VIRTUAL:
                    handleIncomingClose();
                    break;
                
                case ServiceLinkProtocol.MESSAGE_VIRTUAL:
                    handleIncomingMessage();
                    break;
                
                case ServiceLinkProtocol.MESSAGE_VIRTUAL_ACK:
                    handleIncomingAck();
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
            if (logger.isInfoEnabled()) {
                logger.info("Cannot send message: not connected to hub");
            }
            return;
        }
             
        if (logger.isInfoEnabled()) {
            logger.info("Sending message to hub: [" + target.toString() 
                    + ", " + targetModule + ", " + opcode + ", " + message + "]");
        }
        
        try { 
            synchronized (this) {
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
            }
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

        if (logger.isInfoEnabled()) {
            logger.info("Requesting client list from hub");
        }
    
        waitConnected(maxWaitTime);
        
        String id = "GetClientsForHub" + getNextSimpleCallbackID();
    
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
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
        
        if (logger.isInfoEnabled()) {
            logger.info("Requesting client list from hub");
        }
        
        waitConnected(maxWaitTime);
        
        String id = "GetAllClients" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
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
            
        if (logger.isInfoEnabled()) {
            logger.info("Requesting hub list from hub");
        }
        
        waitConnected(maxWaitTime);
                
        String id = "GetHubs" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
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
        
        if (logger.isInfoEnabled()) {
            logger.info("Requesting hub details from hub");
        }
        
        waitConnected(maxWaitTime);        
        
        String id = "GetHubDetails" + getNextSimpleCallbackID();
                
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
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

        waitConnected(maxWaitTime);
        
        if (logger.isInfoEnabled()) {
            logger.info("Requesting direction to client " + client + " from hub");
        }
        
        String id = "GetDirection" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
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
        
        waitConnected(maxWaitTime);
        
        return hubAddress;
    }
    
    public int createVirtualConnection(SocketAddressSet target, String info, 
            int timeout) throws IOException {
        
        IOException exc = null;
        
        VirtualConnection vc = vcs.newVC();
        
        if (logger.isInfoEnabled()) {
            logger.info("Creating virtual connection: " + vc.number);
        }
        
        String id = "CreateVirtual" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
        try { 
        
            synchronized (this) {         
                out.write(ServiceLinkProtocol.CREATE_VIRTUAL);
                out.writeUTF(id);
                out.writeInt(vc.number);
                out.writeInt(timeout);                          
                out.writeUTF(target.toString());
                out.writeUTF(info);                                   
                out.flush();            
            } 

            String [] reply = (String []) tmp.getReply();
        
            if (reply != null && reply.length > 0) { 
                
                if (reply[0].equals("OK")) {
                    // ...
                    int credits = Integer.parseInt(reply[1]);                    
                    vc.init(credits);
                } else if (reply[0].equals("DENIED")) { 
                    vcs.freeVC(vc);
                    exc = new IOException("Failed to setup connection to " 
                            + target + ": " + reply[1]);
                } else { 
                    vcs.freeVC(vc);
                    exc = new IOException("Failed to setup connection to " 
                            + target + ": got junk reply from hub: " 
                            + Arrays.deepToString(reply));                    
                } 

            } else {                  
                vcs.freeVC(vc);
                exc = new IOException("Failed to setup connection to " 
                        + target + ": got empty reply from hub!");                    
            } 

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
        
        if (exc != null) {
            // got an exception!
            throw exc;
        }
        
        return vc.number;
    }    
    
    public void closeVirtualConnection(int index) throws IOException {

        IOException exc = null;
        
        if (logger.isInfoEnabled()) {
            logger.info("Closing virtual connection: " + index);
        }

        VirtualConnection vc = vcs.getVC(index);
        
        if (vc == null) { 
            logger.warn("Virtual connection: " + index + " does not exist!");
            return;
        }
        
        String id = "CloseVirtual" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
        try {         
            synchronized (this) {         
                out.write(ServiceLinkProtocol.CLOSE_VIRTUAL);
                out.writeUTF(id);
                out.writeInt(index);
                out.flush();            
            } 

            String [] reply = (String []) tmp.getReply();
        
            if (reply != null && reply.length > 0) { 
                
                if (reply[0].equals("OK")) {
                    vcs.freeVC(vc);
                } else if (reply[0].equals("DENIED")) {
                    vcs.freeVC(vc);                    
                    exc = new IOException("Failed to cleanly close virtual " +
                            "connection " + index + ": " + reply[1]);
                } else { 
                    vcs.freeVC(vc);
                    exc = new IOException("Failed to close virtual connection "
                            + index + ": got junk reply from hub: "
                            + Arrays.deepToString(reply));                    
                } 

            } else {
                vcs.freeVC(vc);
                exc = new IOException("Failed to close virtual connection " 
                        + index + ": got illegal reply from hub!");                    
            } 

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
            throw new IOException("Connection to hub lost!");
        } finally { 
            removeCallback(id);
        }
        
        if (exc != null) { 
            throw exc;
        }                
    }
    
    public void sendVirtualMessage(int index, byte [] message, int off, 
            int len) throws IOException {
    
        if (logger.isInfoEnabled()) {
            logger.info("Sending virtual message: " + index);
        }

        VirtualConnection vc = vcs.getVC(index);
        
        if (vc == null) { 
            logger.warn("Virtual connection: " + index + " does not exist!");            
            throw new IOException("Not connected");
        }
        
        // May block until credits are available....
        vc.getCredit();
                
        try { 
            synchronized (this) {
                out.write(ServiceLinkProtocol.MESSAGE_VIRTUAL);           
                out.writeInt(index);
                out.writeInt(len);
                out.write(message, off, len);
                out.flush();
            }                 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
        }                
    }
        
    public void ackVirtualMessage(int index, byte [] message) throws IOException {
    
        if (logger.isInfoEnabled()) {
            logger.info("Ack virtual message: " + index);
        }

        VirtualConnection vc = vcs.getVC(index);
        
        if (vc == null) { 
            logger.warn("Virtual connection: " + index + " does not exist!");            
            throw new IOException("Not connected");
        }
                
        try { 
            synchronized (this) {
                out.write(ServiceLinkProtocol.MESSAGE_VIRTUAL_ACK);           
                out.writeInt(index);
                out.flush();
            }                 
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnection();
        }        
    }
        
    public boolean registerProperty(String tag, String value) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting info registration: " + tag + " " + value);
        }
        
        String id = "RegisterInfo" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.REGISTER_PROPERTY);
                out.writeUTF(id);
                out.writeUTF(tag);
                out.writeUTF(value);
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

    public boolean updateProperty(String tag, String value) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting info update: " + tag + " " + value);
        }
        
        waitConnected(maxWaitTime);
        
        
        String id = "UpdateInfo" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.UPDATE_PROPERTY);
                out.writeUTF(id);
                out.writeUTF(tag);
                out.writeUTF(value);
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

    public boolean removeProperty(String tag) throws IOException {
        
        if (logger.isInfoEnabled()) {
            logger.info("Requesting info removal: " + tag);
        }        
        
        waitConnected(maxWaitTime);
        
        String id = "RemoveInfo" + getNextSimpleCallbackID();
        
        SimpleCallBack tmp = new SimpleCallBack();        
        register(id, tmp);
        
        try {
            synchronized (this) {         
                out.write(ServiceLinkProtocol.REMOVE_PROPERTY);
                out.writeUTF(id);
                out.writeUTF(tag);
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
        
    public SocketAddressSet findSharedHub(SocketAddressSet myMachine, 
            SocketAddressSet targetMachine) {
        
        try {             
            waitConnected(maxWaitTime);
        } catch (IOException e) {
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

    public static ServiceLink getServiceLink(SocketAddressSet address, 
            SocketAddressSet myAddress) { 

        // TODO: cache service linkes here ? Shared a link between multiple 
        // clients that use the same hub ?  
        
        if (address == null) {  
            throw new NullPointerException("Hub address is null!");
        }
                
        if (myAddress == null) { 
            throw new NullPointerException("Local address is null!");
        }
        
        try { 
            return new ServiceLink(address, myAddress);                 
        } catch (Exception e) {
            logger.warn("ServiceLink: Failed to connect to hub!", e);
            return null;
        }
    }
}
