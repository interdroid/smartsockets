package ibis.smartsockets.hub.servicelink;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.hub.ConnectionProtocol;
import ibis.smartsockets.hub.connections.MessageForwarderProtocol;
import ibis.smartsockets.hub.connections.VirtualConnectionIndex;
import ibis.smartsockets.util.TypedProperties;
import ibis.util.ThreadPool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServiceLink implements Runnable {

    // TODO: create different loggers here!
    private static final Logger logger = LoggerFactory
            .getLogger("ibis.smartsockets.hub.servicelink");

    private static final Logger statslogger = LoggerFactory
            .getLogger("ibis.smartsockets.statistics");

    private static final int TIMEOUT = 5000;

    private static final int DEFAULT_WAIT_TIME = 10000;

    private final HashMap<String, Object> callbacks 
        = new HashMap<String, Object>();

    private final HashMap<Integer, Object> infoRequests 
        = new HashMap<Integer, Object>();

    private final DirectSocketFactory factory;

    private final DirectSocketAddress myAddress;
    
    private final List<DirectSocketAddress> hubs;
    
    private DirectSocketAddress hubAddress;

    private boolean connected = false;
    
    private DirectSocket hub;
    
    private DataOutputStream out;

    private DataInputStream in;

    private int nextCallbackID = 0;

    private int maxWaitTime = DEFAULT_WAIT_TIME;

    private final VirtualConnectionIndex vcIndex = new VirtualConnectionIndex(
            true);

    private VirtualConnectionCallBack vcCallBack = null;

    private int sendBuffer = -1;

    private int receiveBuffer = -1;

    // Some statistics
    private long incomingConnections;

    private long acceptedIncomingConnections;

    private long rejectedIncomingConnections;

    private long failedIncomingConnections;

    private long outgoingConnections;

    private long acceptedOutgoingConnections;

    private long rejectedOutgoingConnections;

    private long failedOutgoingConnections;

    private long incomingBytes;

    private long outgoingBytes;

    private long incomingDataMessages;

    private long outgoingDataMessages;

    private long incomingMetaMessages;

    private long outgoingMetaMessages;

    private final int virtualHubPort;
    
    private final long maxReconnect;
    
    private final boolean forceConnection;

    private ServiceLink(List<DirectSocketAddress> hubs,
            DirectSocketAddress myAddress, int sendBuffer, int receiveBuffer, 
            int virtualHubPort, long maxReconnect, boolean forceConnection) throws IOException {

        this.hubs = hubs;
        this.sendBuffer = sendBuffer;
        this.receiveBuffer = receiveBuffer;
        this.myAddress = myAddress;

        this.maxReconnect = maxReconnect;
        this.forceConnection = forceConnection;
        
        this.virtualHubPort = virtualHubPort;

        factory = DirectSocketFactory.getSocketFactory();
        
        ThreadPool.createNew(this, "ServiceLink Message Reader");
    }

    public synchronized void registerVCCallBack(VirtualConnectionCallBack cb) {
        vcCallBack = cb;
    }
    
    public synchronized VirtualConnectionCallBack getVCCallBack() {
        return vcCallBack;
    }
    
    public void register(String identifier, CallBack callback) {

        synchronized (callbacks) { 
            if (callbacks.containsKey(identifier)) {
                logger.warn("ServiceLink: refusing to override callback "
                        + identifier, new Exception());
                
                return;
            }

            callbacks.put(identifier, callback);
        }
    }

    protected Object findCallback(String identifier) {        
        synchronized (callbacks) {         
            return callbacks.get(identifier);
        }
    }

    protected void removeCallback(String identifier) {
        synchronized (callbacks) {
            callbacks.remove(identifier);
        }
    }

    protected void registerInfoRequest(Integer identifier) {

        synchronized (infoRequests) {
            if (infoRequests.containsKey(identifier)) {
                logger.warn("ServiceLink: refusing to override simple callback "
                        + identifier, new Exception());

                return;
            }

            infoRequests.put(identifier, null);
        }
    }

    protected void removeInfoRequest(Integer identifier) {
        synchronized (infoRequests) {
            infoRequests.remove(identifier);
        }
    }

    protected void storeInfoReply(Integer identifier, Object value) {
        synchronized (infoRequests) {
            if (infoRequests.containsKey(identifier)) {
                infoRequests.put(identifier, value);
                infoRequests.notifyAll();
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Dropped info reply for: " + identifier + " ("
                        + value + ")");
                }
            }
        }
    }

    protected Object getInfoReply(Integer identifier) {

        synchronized (infoRequests) {

            Object result = infoRequests.get(identifier);

            while (result == null) {
                try {
                    infoRequests.wait();
                } catch (InterruptedException e) {
                    // ignore
                }

                result = infoRequests.get(identifier);
            }

            infoRequests.remove(identifier);

            return result;
        }
    }

    protected boolean getInfoReply(Integer identifier, int value) {

        Object result = getInfoReply(identifier);

        if (result instanceof Integer) {
            return ((Integer) result).intValue() == value;
        }

        return false;
    }

    private synchronized void setConnected(boolean value) {
        connected = value;
        notifyAll();
    }

    private synchronized boolean getConnected() {
        return connected;
    }

    public synchronized void waitConnected(int time) throws IOException {

        if (time < 0) {
            
            if (!connected) { 
                throw new IOException("No connection to hub!");
            }
        
            return;
        }
        
        long deadline = System.currentTimeMillis() + time;
        long timeleft = time;
        
        while (!connected) {
            
            try {
                if (time > 0) { 
                    wait(timeleft);
                } else { 
                    wait();
                }
            } catch (InterruptedException e) {
                // ignore
            }
            
            if (!connected && time > 0) { 
                timeleft = deadline - System.currentTimeMillis();
                
                if (timeleft <= 0) { 
                    throw new IOException("No connection to hub!");
                }
            }
        }
    }

    private synchronized void closeConnectionToHub() {

        // TODO: is the synchronization OK here ???
        if (!getConnected()) {
            return;
        }

        setConnected(false);

        DirectSocketFactory.close(hub, out, in);

        // Should close virtual connections here ? 

        /*      Long [] tmp = credits.keySet().toArray(new Long[0]);
         
         for (long l : tmp) { 
         closeConnection(l);
         }
         */
    }

    private void connectToHub(DirectSocketAddress address) throws IOException {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Service link attempting to connect to hub: " 
                        + address);
            }
            
            // Create a connection to the hub
            hub = factory.createSocket(address, TIMEOUT, 0, sendBuffer,
                    receiveBuffer, null, false, virtualHubPort);

            hub.setTcpNoDelay(true);

            if (logger.isDebugEnabled()) {
                logger.debug("Service link send buffer = "
                            + hub.getSendBufferSize());
                logger.debug("Service link recv buffer = "
                    + hub.getReceiveBufferSize());
            }

            out = new DataOutputStream(new BufferedOutputStream(hub
                    .getOutputStream()));

            in = new DataInputStream(new BufferedInputStream(hub
                    .getInputStream()));

            // Ask if we are allowed to join
            out.write(ConnectionProtocol.SERVICELINK_CONNECT);
            out.writeUTF(myAddress.toString());
            out.flush();

            // Get the result
            int reply = in.read();

            // Throw an exception if the hub refuses our conenction
            if (reply != ConnectionProtocol.CONNECTION_ACCEPTED) {
                throw new IOException("Hub denied connection request (got: "
                        + reply);
            }

            // If the connection is accepted, the hub will give us its full 
            // address (since the user supplied one may be a partial).  
            hubAddress = DirectSocketAddress.getByAddress(in.readUTF());

            if (logger.isInfoEnabled()) {
                logger.info("Hub at " + address + " accepted connection, "
                        + "it's real address is: " + hubAddress);
            }

            hub.setSoTimeout(0);

            setConnected(true);
        } catch (IOException e) {
            logger.info("Connection setup to hub at " + address + " failed: ",
                    e);
            closeConnectionToHub();
            throw e;
        }
    }

    private final void skip(int bytes) throws IOException {
        while (bytes > 0) {
            bytes -= in.skip(bytes);
        }
    }

    private void handleInfoMessage() throws IOException {

        DirectSocketAddress source = DirectSocketAddress.read(in);
        DirectSocketAddress sourceHub = DirectSocketAddress.read(in);

        // since we have reached our destination, the hop count and 
        // target addresses are not used anymore..
        skip(4);
        
        boolean returnedToSender = in.readBoolean();
        
        DirectSocketAddress.skip(in);
        DirectSocketAddress.skip(in);

        String targetModule = in.readUTF();
        int opcode = in.readInt();

        byte[][] message = readMessageBlob();

        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received message for " + targetModule 
                    + " (returnToSender: " + returnedToSender + ")");
        }

        CallBack cb = (CallBack) findCallback(targetModule);

        if (cb == null) {
            logger.warn("ServiceLink: Callback " + targetModule + " not found");
        } else {
            cb.gotMessage(source, sourceHub, opcode, returnedToSender, message);
        }

        incomingMetaMessages++;
    }

    private void handleInfo() throws IOException {

        int id = in.readInt();
        int count = in.readInt();

        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received info for " + id + ". "
                    + " Receiving " + count + " strings....");
        }

        String[] info = new String[count];

        for (int i = 0; i < count; i++) {
            info[i] = in.readUTF();
            if (logger.isInfoEnabled()) {
                logger.info(i + ": " + info[i]);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("done receiving info");
        }

        storeInfoReply(id, info);
    }

    private void handlePropertyAck() throws IOException {

        int id = in.readInt();
        int value = in.readInt();

        if (logger.isInfoEnabled()) {
            logger.info("ServiceLink: Received property ack for " + id + " "
                    + " (" + value + ")");
        }

        storeInfoReply(id, value);
    }

    private void handleIncomingConnection() throws IOException {

        incomingConnections++;

        DirectSocketAddress source = DirectSocketAddress.read(in);
        DirectSocketAddress sourceHub = DirectSocketAddress.read(in);

        DirectSocketAddress.skip(in);
        DirectSocketAddress.skip(in);

        long index = in.readLong();

        int timeout = in.readInt();
        int port = in.readInt();
        int fragment = in.readInt();
        int buffer = in.readInt();

        if (logger.isInfoEnabled()) {
            logger
                    .info("ServiceLink: Received request for incoming connection "
                            + "from " + source + " (" + index + ")");
        }

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {

            if (logger.isInfoEnabled()) {
                logger.info("DENIED connection: " + index + ": no callback!");
            }

            nackVirtualConnection(index, ServiceLinkProtocol.ERROR_NO_CALLBACK);
            return;
        }

        // Forward the connect call to the module responsible. This call will 
        // result in an invocation of (n)ackVirtualConnection. 
        vcb.connect(source, sourceHub, port, fragment, buffer, timeout, index);

        // Connection is now pending in the backlog of a serversocket somewhere.
        // It may be accepted or rejected at any time.
        if (logger.isInfoEnabled()) {
            logger.info("QUEUED connection: " + index + ": waiting for accept");
        }
    }

    private void handleIncomingConnectionACK() throws IOException {

        long index = in.readLong();

        int fragment = in.readInt();
        int buffer = in.readInt();

        //      System.err.println("***** ACK IN " + index);

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {

            if (logger.isInfoEnabled()) {
                logger.info("Cannot deliver ACK: " + index + ": no callback!");
            }

            // Send a close back...
            closeVirtualConnection(index);
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Delivering ACK: " + index + "(" + fragment + ", "
                    + buffer + ")");
        }

        vcb.connectACK(index, fragment, buffer);
    }

    private void handleIncomingConnectionACKACK() throws IOException {

        long index = in.readLong();
        boolean succes = in.readBoolean();

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {

            if (logger.isInfoEnabled()) {
                logger.info("Cannot deliver ACK ACK: " + index
                        + ": no callback!");
            }

            // Send a close back if someone is waiting for us...
            if (succes) {
                closeVirtualConnection(index);
            }

            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Delivering ACK ACK: " + index);
        }

        vcb.connectACKACK(index, succes);
    }

    private void handleIncomingConnectionNACK() throws IOException {

        long index = in.readLong();
        byte reason = in.readByte();

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {

            if (logger.isInfoEnabled()) {
                logger.info("Cannot deliver NACK: " + index + ": no callback!");
            }

            // No need to send a close back, since it was a NACK.
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Delivering NACK: " + index);
        }

        vcb.connectNACK(index, reason);
    }

    /*    
     public void rejectIncomingConnection(long index) {
     
     // The incoming connection 'index' was rejected.
     if (logger.isInfoEnabled()) {
     logger.info("REJECTED connection: (" + index 
     + "): serversocket did not accept");
     }
     
     Credits c = credits.get(index);
     
     if (c == null) { 
     failedIncomingConnections++;
     
     logger.warn("Cannot close connection: " + index 
     + " since it does not exist (anymore)!");            
     return;
     }
     
     try { 
     synchronized (out) {         
     out.write(MessageForwarderProtocol.CREATE_VIRTUAL_ACK);
     out.writeLong(index);
     out.writeUTF("DENIED");  
     out.writeUTF("Connection refused");
     out.flush();
     }
     } catch (IOException e) {
     logger.warn("ServiceLink: Exception while writing to hub!", e);
     closeConnectionToHub();                      
     }
     
     rejectedIncomingConnections++;
     }
     */

    private void disconnectCallback(long index) {

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {
            logger.warn("Cannot forward disconnect(" + index
                    + "): no callback!");
            return;
        }

        vcb.disconnect(index);
    }

    /*
     public boolean acceptIncomingConnection(long index) throws IOException { 
     
     acceptedIncomingConnections++;
     
     // The incoming connection 'index' was accepted. The only problem is 
     // that we are not sure if we are in time. We therefore send a reply, 
     // for which we get a reply back again...
     if (logger.isInfoEnabled()) {
     logger.info("ACCEPTED connection: (" + index + ")");
     }

     Credits c = credits.get(index);
     
     if (c == null) { 
     if (logger.isInfoEnabled()) {
     logger.info("ACCEPTED connection disappeared: " 
     + " (" + index + "): client has already closed!");
     }
     
     failedIncomingConnections++;
     disconnectCallback(index);
     return false;
     }
     
     try { 
     synchronized (out) {         
     out.write(MessageForwarderProtocol.CREATE_VIRTUAL_ACK);
     out.writeLong(index);
     out.writeUTF("OK");  
     out.writeUTF("" + maxCredits);                    
     out.flush();
     }
     
     } catch (IOException e) {
     logger.warn("ServiceLink: Exception while writing to hub!", e);
     closeConnectionToHub();
     throw new IOException("Connection to hub lost!");            
     }
     
     acceptedIncomingConnections++;
     
     return true;
     }
     */

    /* 
     private void closeConnection(long index) { 
     
     Credits c = credits.remove(index);
     
     if (c == null) {
     // This may happen if we closed the connection while the other
     // sides close was in transit...
     if (logger.isInfoEnabled()) { 
     logger.info("Cannot close connection: " + index 
     + " since it does not exist (anymore)!");
     }
     return;
     }
     
     disconnectCallback(index); 
     }*/

    private void handleIncomingClose() throws IOException {

        long index = in.readLong();

        if (logger.isDebugEnabled()) {
            logger.debug("Got close for connection: " + index);
        }

        //   System.err.println("***** CLOSE " + index);

        disconnectCallback(index);
    }

    private void handleIncomingMessage() throws IOException {

        long index = in.readLong();
        int len = in.readInt();

        if (logger.isDebugEnabled()) {
            logger.debug("Reading virtual message(" + len
                    + ") for connection: " + index);
        }

        incomingDataMessages++;
        incomingBytes += len;

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {
            logger.warn("Received virtual message(" + len
                    + ") for connection: " + index + " which doesn't exist!!");

            // Remove the message from the stream!
            skip(len);
            closeVirtualConnection(index);
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Delivering virtual message(" + len + ") for"
                    + " connection: " + index);
        }

        if (!vcb.gotMessage(index, len, in)) {
            if (logger.isInfoEnabled()) {
                logger.debug("Message for " + index + " not read!");
            }
            skip(len);
        }
    }

    private void handleIncomingAck() throws IOException {

        long index = in.readLong();
        int data = in.readInt();

        if (logger.isDebugEnabled()) {
            logger.debug("Got Message ACK for connection: " + index);
        }

        VirtualConnectionCallBack vcb = getVCCallBack();
        
        if (vcb == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Cannot delivering virtual message ACK for"
                        + " connection: " + index);
            }

            closeVirtualConnection(index);
            return;
        }

        vcb.gotMessageACK(index, data);
    }

    void receiveMessages() {

        while (getConnected()) {

            try {
                int header = in.read();

                if (logger.isDebugEnabled()) {
                    logger.debug("Servicelink got message (type: " + header
                            + ")");
                }

                switch (header) {
                case -1:
                    closeConnectionToHub();
                    break;

                case MessageForwarderProtocol.INFO_MESSAGE:
                    handleInfoMessage();
                    break;

                case MessageForwarderProtocol.CREATE_VIRTUAL:
                    handleIncomingConnection();
                    break;

                case MessageForwarderProtocol.CREATE_VIRTUAL_ACK:
                    handleIncomingConnectionACK();
                    break;

                case MessageForwarderProtocol.CREATE_VIRTUAL_ACK_ACK:
                    handleIncomingConnectionACKACK();
                    break;

                case MessageForwarderProtocol.CREATE_VIRTUAL_NACK:
                    handleIncomingConnectionNACK();
                    break;

                case MessageForwarderProtocol.CLOSE_VIRTUAL:
                    handleIncomingClose();
                    break;

                case MessageForwarderProtocol.MESSAGE_VIRTUAL:
                    handleIncomingMessage();
                    break;

                case MessageForwarderProtocol.MESSAGE_VIRTUAL_ACK:
                    handleIncomingAck();
                    break;

                case ServiceLinkProtocol.INFO_REPLY:
                    handleInfo();
                    break;

                case ServiceLinkProtocol.PROPERTY_ACK:
                    handlePropertyAck();
                    break;

                default:
                    logger.warn("ServiceLink: Received unknown opcode!: "
                            + header);

                    closeConnectionToHub();
                    break;
                }

            } catch (IOException e) {
                logger.warn("ServiceLink: Exception while receiving!", e);
                closeConnectionToHub();
            }
        }
    }

    private byte[][] readMessageBlob() throws IOException {

        byte[][] message = null;

        int bytes = in.readInt();

        if (bytes > 0) {
            int len = in.readInt();
            message = new byte[len][];

            for (int i = 0; i < len; i++) {

                int tmp = in.readInt();
                message[i] = new byte[tmp];

                if (tmp > 0) {
                    in.readFully(message[i]);
                }
            }
        }

        return message;
    }

    private void writeMessageBlob(byte[][] message) throws IOException {

        if (message == null) {
            out.writeInt(0);
        } else {

            int totalBytes = 4;

            for (byte[] b : message) {

                totalBytes += 4;

                if (b != null) {
                    totalBytes += b.length;
                }
            }

            out.writeInt(totalBytes);
            out.writeInt(message.length);

            for (byte[] b : message) {
                if (b == null) {
                    out.writeInt(0);
                } else {
                    out.writeInt(b.length);
                    out.write(b);
                }
            }
        }
    }

    public void send(DirectSocketAddress target, DirectSocketAddress targetHub,
            String targetModule, int opcode, byte[][] message) {

        if (!getConnected()) {
            if (logger.isInfoEnabled()) {
                logger.info("Cannot send message: not connected to hub");
            }
            return;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Sending message to hub: [" + target.toString() + ", "
                    + targetModule + ", " + opcode + ", " 
                    + Arrays.deepToString(message) + "]");
        }

        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.INFO_MESSAGE);

                DirectSocketAddress.write(myAddress, out);
                DirectSocketAddress.write(hubAddress, out); // may be null

                // hops left is not used here...
                out.writeInt(-1);
                
                // return to sender is set to false by default
                out.writeBoolean(false);
                
                DirectSocketAddress.write(target, out);
                DirectSocketAddress.write(targetHub, out); // may be null

                out.writeUTF(targetModule);
                out.writeInt(opcode);

                writeMessageBlob(message);

                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
        }

        outgoingMetaMessages++;
    }

    /*
     public void send(SocketAddressSet target, 
     SocketAddressSet targetHub, String targetModule, int opcode, 
     String message) { 
     
     send(target, targetHub, targetModule, opcode, 
     new byte [][] { message.getBytes() });
     }*/

    private synchronized int getNextSimpleCallbackID() {
        return nextCallbackID++;
    }

    public ClientInfo[] localClients() throws IOException {
        return clients(hubAddress, "");
    }

    public ClientInfo[] localClients(String tag) throws IOException {
        return clients(hubAddress, tag);
    }

    public ClientInfo[] clients(DirectSocketAddress hub) throws IOException {
        return clients(hub, "");
    }

    private ClientInfo[] convertToClientInfo(String[] message) {

        ClientInfo[] result = new ClientInfo[message.length];

        for (int i = 0; i < message.length; i++) {
            result[i] = new ClientInfo(message[i]);
        }

        return result;
    }

    private HubInfo[] convertToHubInfo(String[] message) {

  //      System.out.println("GOT MESSAGE: " + Arrays.deepToString(message));
        
        HubInfo[] result = new HubInfo[message.length];

        for (int i = 0; i < message.length; i++) {
            result[i] = new HubInfo(message[i]);
        }

        return result;
    }

    public ClientInfo[] clients(DirectSocketAddress hub, String tag)
            throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting client list from hub");
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.CLIENTS_FOR_HUB);
                out.writeInt(id);
                out.writeUTF(hub.toString());
                out.writeUTF(tag);
                out.flush();
            }

            return convertToClientInfo((String[]) getInfoReply(id));
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public ClientInfo[] clients() throws IOException {
        return clients("");
    }

    public ClientInfo[] clients(String tag) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting client list from hub");
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.ALL_CLIENTS);
                out.writeInt(id);
                out.writeUTF(tag);
                out.flush();
            }

            return convertToClientInfo((String[]) getInfoReply(id));
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public DirectSocketAddress[] hubs() throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting hub list from hub");
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.HUBS);
                out.writeInt(id);
                out.flush();
            }

            return DirectSocketAddress
                    .convertToSocketAddressSet((String[]) getInfoReply(id));

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public void addHubs(DirectSocketAddress... hubs) {
        // TODO: implement!
    }
    
    public void addHubs(String... hubs) {
        // TODO: implement!
    }
        
    public HubInfo[] hubDetails() throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting hub details from hub");
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.HUB_DETAILS);
                out.writeInt(id);
                out.flush();
            }

            return convertToHubInfo((String[]) getInfoReply(id));

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public DirectSocketAddress[] locateClient(String client) throws IOException {

        waitConnected(maxWaitTime);

        if (logger.isInfoEnabled()) {
            logger.info("Requesting direction to client " + client
                    + " from hub");
        }

        Integer id = getNextSimpleCallbackID();
        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.DIRECTION);
                out.writeInt(id);
                out.writeUTF(client);
                out.flush();
            }

            return DirectSocketAddress
                    .convertToSocketAddressSet((String[]) getInfoReply(id));

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public DirectSocketAddress getAddress() throws IOException {

        waitConnected(maxWaitTime);

        return hubAddress;
    }

    /*
     private void registerConnectionACK(long index) {        
     synchronized (connectionACKs) {
     connectionACKs.put(index, null);
     }
     }
     
     private void gotConnectionACK(long index, String [] result) {    
     
     //logger.warn("Got ACK: " + index + ": " + Arrays.deepToString(result));
     
     synchronized (connectionACKs) { 
     if (connectionACKs.containsKey(index)) { 
     
     //   logger.warn("Delivering ACK: " + index);
     
     connectionACKs.put(index, result);
     connectionACKs.notifyAll();
     return;
     }             
     }
     
     // If we end up here, there was no one waiting for a connection ACK 
     // for connection 'index' anymore. The connector probably timed out.
     // To inform the other side of this, we send a 'close' back as a reply.
     
     //  logger.warn("Deleting ACK: " + index);
     
     try { 
     closeVirtualConnection(index);
     } catch (IOException e) {
     // ignored... we did our best....
     }
     }
     
     public void waitForConnectionACK(long index, int timeout) 
     throws IOException { 

     // logger.warn("Waiting for ACK: " + index);
     
     byte result;
     
     synchronized (connectionACKs) {
     
     long endTime = System.currentTimeMillis() + timeout;
     long timeLeft = timeout;
     
     result = connectionACKs.get(index);
     
     while (result == null && timeLeft >= 0) { 
     try { 
     connectionACKs.wait(timeLeft);
     } catch (InterruptedException e) {
     // ignore
     }
     
     if (timeout > 0) { 
     timeLeft = endTime - System.currentTimeMillis();
     
     // Prevents us from accidently falling into a wait(0)!!
     if (timeLeft == 0) { 
     timeLeft = -1;
     }
     }
     
     result = connectionACKs.get(index);
     }
     
     result = connectionACKs.remove(index);
     }

     // No result ? Timeout!
     if (result == null) {
     failedOutgoingConnections++;
     throw new SocketTimeoutException("Connect " + index 
     + " timed out!");
     }
     
     // We have a result, but it may not be positive...
     if (result[0].equals("DENIED")) {
     
     if (result[1].equals("Unknown host")) { 
     failedOutgoingConnections++;
     throw new UnknownHostException(); 
     }
     
     if (result[1].equals("Connection refused")) {
     rejectedOutgoingConnections++;
     throw new ConnectException(); 
     }
     
     failedOutgoingConnections++;
     throw new IOException("Connection " + index + " failed: " + result[1]);
     } 
     
     // Sanity check
     if (!result[0].equals("OK")) {
     failedOutgoingConnections++;
     throw new IOException("Connection + " + index + " failed: " + result[0]);
     } 
     
     int tmp = DEFAULT_CREDITS; 
     
     try { 
     tmp = Integer.parseInt(result[1]);
     } catch (Exception e) {
     logger.warn("Failed to parse number of credits:" + result[1]);
     }
     
     System.err.println("Created virtual stream with " + tmp + " credits!");
     
     // Otherwise, the connection is accepted.
     credits.put(index, new Credits(tmp));
     acceptedOutgoingConnections++;
     }
     */

    public long getConnectionNumber() {
        return vcIndex.nextIndex();
    }

    public void createVirtualConnection(long index, DirectSocketAddress target,
            DirectSocketAddress targetHub, int port, int fragment, int buffer,
            int timeout) throws IOException {

        if (!getConnected()) {
            throw new IOException("No connection to hub!");
        }

        if (timeout < 0) {
            timeout = 0;
        }

        if (logger.isInfoEnabled()) {
            logger.debug("Creating virtual connection: " + index);
        }

        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL);

                DirectSocketAddress.write(myAddress, out);
                DirectSocketAddress.write(hubAddress, out);

                DirectSocketAddress.write(target, out);
                DirectSocketAddress.write(targetHub, out);

                out.writeLong(index);

                out.writeInt(timeout);
                out.writeInt(port);
                out.writeInt(fragment);
                out.writeInt(buffer);

                out.flush();
            }

            outgoingConnections++;

        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        }
    }

    public void ackVirtualConnection(long index, int fragment, int buffer) {

        if (!getConnected()) {
            logger.warn("Failed to ACK virtual connection: no connection "
                    + "to hub");
        }

        //   System.err.println("#### ACK " + index);

        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL_ACK);
                out.writeLong(index);
                out.writeInt(fragment);
                out.writeInt(buffer);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing ACK to hub!", e);
            closeConnectionToHub();
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Send ACK for connection: " + index + " (" + fragment
                    + ", " + buffer + ")");
        }
    }

    public void ackAckVirtualConnection(long index, boolean success) {

        if (!getConnected()) {
            logger.warn("Failed to ACK virtual connection: no connection "
                    + "to hub");
        }

        //   System.err.println("#### ACK ACK " + index);

        try {
            synchronized (out) {
                out.writeByte(MessageForwarderProtocol.CREATE_VIRTUAL_ACK_ACK);
                out.writeLong(index);
                out.writeBoolean(success);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing ACK to hub!", e);
            closeConnectionToHub();
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Send ACK for connection: " + index + ")");
        }
    }

    public void nackVirtualConnection(long index, byte reason) {

        rejectedIncomingConnections++;

        if (!getConnected()) {
            logger.warn("Failed to NACK virtual connection: no connection "
                    + "to hub");
            return;
        }

        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.CREATE_VIRTUAL_NACK);
                out.writeLong(index);
                out.writeByte(reason);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing NACK to hub!", e);
            closeConnectionToHub();
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Send NACK for connection: " + index + "(" + reason
                    + ")");
        }
    }

    public void closeVirtualConnection(long index) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.debug("Closing virtual connection: " + index);
        }
        
        if (!getConnected()) {
            throw new IOException("No connection to hub");
        }

        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.CLOSE_VIRTUAL);
                out.writeLong(index);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        }

        if (logger.isDebugEnabled()) {
            logger.warn("Virtual connection " + index + " closed!");
        }
    }

    public void sendVirtualMessage(long index, byte[] message, int off,
            int len, int timeout) throws IOException {

        if (!getConnected()) {
            throw new IOException("No connection to hub!");
        }

        if (logger.isInfoEnabled()) {
            logger.info("Sending virtual message for connection: " + index);
        }

        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.MESSAGE_VIRTUAL);
                out.writeLong(index);
                out.writeInt(len);
                out.write(message, off, len);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
        }

        outgoingDataMessages++;
        outgoingBytes += len;
        //System.err.println("W");
    }

    public void ackVirtualMessage(long index, int data) throws IOException {

        if (!getConnected()) {
            throw new IOException("No connection to hub!");
        }

        if (logger.isInfoEnabled()) {
            logger.info("Ack virtual message: " + index);
        }

        try {
            synchronized (out) {
                out.write(MessageForwarderProtocol.MESSAGE_VIRTUAL_ACK);
                out.writeLong(index);
                out.writeInt(data);
                out.flush();
            }
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
        }

        //System.err.println("A");
    }

    public boolean registerProperty(String tag, String value)
            throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting info registration: " + tag + " " + value);
        }

        if (value == null) {
            value = "";
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.REGISTER_PROPERTY);
                out.writeInt(id);
                out.writeUTF(tag);
                out.writeUTF(value);
                out.flush();
            }

            return getInfoReply(id, ServiceLinkProtocol.PROPERTY_ACCEPTED);
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public boolean updateProperty(String tag, String value) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting info update: " + tag + " " + value);
        }

        if (value == null) {
            value = "";
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.UPDATE_PROPERTY);
                out.writeInt(id);
                out.writeUTF(tag);
                out.writeUTF(value);
                out.flush();
            }

            return getInfoReply(id, ServiceLinkProtocol.PROPERTY_ACCEPTED);
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }

    public boolean removeProperty(String tag) throws IOException {

        if (logger.isInfoEnabled()) {
            logger.info("Requesting info removal: " + tag);
        }

        waitConnected(maxWaitTime);

        Integer id = getNextSimpleCallbackID();

        registerInfoRequest(id);

        try {
            synchronized (out) {
                out.write(ServiceLinkProtocol.REMOVE_PROPERTY);
                out.writeInt(id);
                out.writeUTF(tag);
                out.flush();
            }

            return getInfoReply(id, ServiceLinkProtocol.PROPERTY_ACCEPTED);
        } catch (IOException e) {
            logger.warn("ServiceLink: Exception while writing to hub!", e);
            closeConnectionToHub();
            throw new IOException("Connection to hub lost!");
        } finally {
            removeInfoRequest(id);
        }
    }
   
    public void printStatistics(String prefix) {

        if (statslogger.isInfoEnabled()) {
/*
            statslogger.info(prefix + " SL In : " + incomingConnections + "/"
                    + acceptedIncomingConnections + "/"
                    + rejectedIncomingConnections + "/"
                    + failedIncomingConnections + " Msg: "
                    + incomingDataMessages + "/" + incomingBytes + "/"
                    + incomingMetaMessages);

            statslogger.info(prefix + " SL Out: " + outgoingConnections + "/"
                    + acceptedOutgoingConnections + "/"
                    + rejectedOutgoingConnections + "/"
                    + failedOutgoingConnections + " Msg: "
                    + outgoingDataMessages + "/" + outgoingBytes + "/"
                    + outgoingMetaMessages);
*/
        }

    }

    public void run() {

        // Connect to the hub and processes the messages it gets. When the 
        // connection is lost, it will try to reconnect.
        
        while (true) {
       
        	int sleep = 1000;
            long end = System.currentTimeMillis() + maxReconnect;
            
        	do {
                if (hubAddress == null) { 
                	// This is the initial connect, where we haven't found a working hub yet....
                    for (DirectSocketAddress a : hubs) { 
                        try {
                            connectToHub(a);
                            hubAddress = a;
                            break;
                        } catch (IOException e) {
                            // Connection setup failed..
                            logger.info("Failed to connect to hub: " + a);
                        }
                    }
                
                    if (!getConnected()) { 
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    }                
                } else { 
                	// This happens when we've lost contact with the hub and try to reconnect.
                    try {
                        connectToHub(hubAddress);
                    } catch (IOException e) {
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException ie) {
                            // ignore
                        }
                    }
                }
                
                if (sleep < 16000) {
                    sleep *= 2;
                }
                
                if (forceConnection && maxReconnect > 0 && System.currentTimeMillis() > end) { 
                	logger.error("Permanent failure of servicelink! -- will exit");
                	// FIXME!
                	System.exit(1);
                }
                
            } while (!getConnected());

            sleep = 1000;
            receiveMessages();
        }

    }

    public static ServiceLink getServiceLink(TypedProperties p,
            List<DirectSocketAddress> hubs, DirectSocketAddress myAddress) {

        // TODO: cache service linkes here ? Shared a link between multiple 
        // clients that use the same hub ?  

        if (hubs == null || hubs.size() == 0) {
            throw new NullPointerException("Hub address is null!");
        }

        if (myAddress == null) {
            throw new NullPointerException("Local address is null!");
        }

        int sendBuffer = -1;
        int receiveBuffer = -1;
        int virtualHubPort = 42;
                
        boolean force = true;
        
        if (p != null) {
            sendBuffer = p.getIntProperty(SmartSocketsProperties.SL_SEND_BUFFER, -1);
            receiveBuffer = p.getIntProperty(SmartSocketsProperties.SL_RECEIVE_BUFFER, -1);
            virtualHubPort = p.getIntProperty(SmartSocketsProperties.HUB_VIRTUAL_PORT, 42);
            force = p.booleanProperty(SmartSocketsProperties.SL_FORCE);
        
        }
        
        long maxReconnect = 0;
        
        if (force) { 
        	maxReconnect = ((long) p.getIntProperty(SmartSocketsProperties.SL_RETRIES)) * 
        			((long) p.getIntProperty(SmartSocketsProperties.SL_TIMEOUT));
        }
        
        try {
            return new ServiceLink(hubs, myAddress, sendBuffer,
                    receiveBuffer, virtualHubPort, maxReconnect, force);

        } catch (Exception e) {
            logger.warn("ServiceLink: Failed to connect to hub!", e);
            return null;
        }
    }
}
