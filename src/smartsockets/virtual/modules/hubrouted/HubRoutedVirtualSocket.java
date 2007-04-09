package smartsockets.virtual.modules.hubrouted;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.Map;

import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.hub.servicelink.ServiceLinkProtocol;
import smartsockets.virtual.TargetOverloadedException;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;
import smartsockets.virtual.VirtualSocketFactory;

public class HubRoutedVirtualSocket extends VirtualSocket {
    
    private final Hubrouted parent;     
    private final ServiceLink serviceLink;
    private long connectionIndex;
        
    private int timeout = 0;
    
    private boolean closed = false;
    
    private HubRoutedOutputStream out;
    private HubRoutedInputStream in;
    
 //   private boolean closeInPending = false;

    private final int localFragmentation; 
    private final int localBufferSize; 
    private final int localMinimalACKSize; 
    
    private int remoteFragmentation; 
    private int remoteBufferSize; 
        
    // Used in the three way handshake during connection setup
    private boolean waitingForACK = false;
    private boolean gotACK = false;
    private int ackResult = 0;
    
    private boolean waitingForACKACK = false;
    private boolean gotACKACK = false;
    private boolean ackACKResult = false;
    
    private boolean gotTargetOverload = false;
    
    protected HubRoutedVirtualSocket(Hubrouted parent, int localFragmentation, 
            int localBufferSize, int localMinimalACKSize, 
            int remoteFragmentation, int remoteBufferSize,  
            VirtualSocketAddress target, ServiceLink serviceLink, 
            long connectionIndex, Map p) {        
        
        super(target);
        
        this.parent = parent;
        this.serviceLink = serviceLink;
        this.connectionIndex = connectionIndex;
        
        this.localFragmentation = localFragmentation;        
        this.localBufferSize = localBufferSize;
        this.localMinimalACKSize = localMinimalACKSize;
        
        this.remoteFragmentation = remoteFragmentation;
        this.remoteBufferSize = remoteBufferSize;
                
        this.out = new HubRoutedOutputStream(this, remoteFragmentation, 
                remoteBufferSize);
        
        this.in = new HubRoutedInputStream(this, localFragmentation, 
                localBufferSize, localMinimalACKSize);            
    }
   
    protected HubRoutedVirtualSocket(Hubrouted parent, int localFragmentation, 
            int localBufferSize, int localMinimalACKSize,  
            VirtualSocketAddress target, ServiceLink serviceLink, Map p) {      
    
        super(target);
        
        this.parent = parent;
        this.serviceLink = serviceLink;
        
        this.localFragmentation = localFragmentation;        
        this.localBufferSize = localBufferSize;
        this.localMinimalACKSize = localMinimalACKSize; 
    }
 
    protected void connectionAccepted(int timeout) throws IOException { 
    
        if (timeout <= 0) { 
            // TODO: Use property here to set default timeout!
            timeout = 120000;
        }
        
        long deadline = System.currentTimeMillis() + timeout;
        long timeleft = timeout;
        
        synchronized (this) {
            waitingForACKACK = true;
        }
        
        
        // Send the ACK to the client side
        serviceLink.ackVirtualConnection(connectionIndex, localFragmentation, 
                localBufferSize);
    
        // Now wait for the ACKACK to come back to us (may time out).
        synchronized (this) {
            while (!gotACKACK) { 
                
                try { 
                    wait(timeleft);
                } catch (InterruptedException e) {
                    // ignore
                }

                if (!gotACKACK) { 
                    timeleft = deadline - System.currentTimeMillis();

                    if (timeleft <= 0) {
                        throw new SocketTimeoutException("Handshake timed out (" + timeout + ", " + timeleft + ") !");
                    }
                }
            }
            
            waitingForACKACK = false;
        }
        
        if (!gotACKACK || !ackACKResult) { 
            throw new SocketException("Handshake failed");
        }
    }
    
    public void connectionRejected(int timeout) { 
        serviceLink.nackVirtualConnection(connectionIndex, 
                ServiceLinkProtocol.ERROR_CONNECTION_REFUSED);         
    }
    
    public void waitForAccept(int timeout) throws IOException {
        
        if (gotTargetOverload) { 
            throw new TargetOverloadedException("Connection refused, " +
                    "target socket overloaded!");
        }
    }
        
    protected void close(boolean local) {
        
        synchronized (this) { 
            if (closed) { 
                return;
            }
            
            closed = true;        
        }
        
        try {
            out.close();
        } catch (Exception e) { 
            // ignore
        }

        try {
            in.close();
        } catch (Exception e) { 
            // ignore
        }
        
        if (local) { 
            parent.close(connectionIndex);
        }
    }
    
    
    public void close() {        
        close(true);
    }
    
    public SocketChannel getChannel() {
        return null;
    }
    
    /*
     * public InetAddress getInetAddress() { return s.getInetAddress(); }
     */
    public InputStream getInputStream() throws IOException {
        return in;
    }
    
    /*
     * public InetAddress getLocalAddress() { return s.getLocalAddress(); }
     */
    
    public int getLocalPort() {
        // TODO: is this right ?
        return 0;
    }
    
    public SocketAddress getLocalSocketAddress() {
        // TODO: implement!!! ?
        return null;
    }
          
    public boolean getOOBInline() throws SocketException {
        return false;
    }
    
    public OutputStream getOutputStream() throws IOException {
        return out;
    }
    
    public int getReceiveBufferSize() throws SocketException {        
        return 0; // s.getReceiveBufferSize();
    }
    
    public boolean getReuseAddress() throws SocketException {
        return false; // s.getReuseAddress();
    }
    
    public int getSendBufferSize() throws SocketException {
        return 0; // s.getSendBufferSize();
    }
    
    public int getSoLinger() throws SocketException {        
        return 0;
    }
    
    public int getSoTimeout() throws SocketException {
        return timeout;
    }
    
    public boolean getTcpNoDelay() throws SocketException {
        return true;
    }
    
    public int getTrafficClass() throws SocketException {
        return 0;
    }
    
    public boolean isBound() {
        return true;
    }
    
    public boolean isClosed() {
        return closed;
    }
    
    public boolean isConnected() {
        return !closed;
    }
    
    public boolean isInputShutdown() {
        return in.closed();
    }
    
    public boolean isOutputShutdown() {
        return out.closed();
    }
    
    public void sendUrgentData(int data) throws IOException {
        // ignored
    }
    
    public void setKeepAlive(boolean on) throws SocketException {
        // ignored
    }
    
    public void setOOBInline(boolean on) throws SocketException {
        // ignored
    }
    
    public void setReceiveBufferSize(int sz) throws SocketException {
        // ignored
    }
    
    public void setReuseAddress(boolean on) throws SocketException {
        // ignored
    }
    
    public void setSendBufferSize(int sz) throws SocketException {
        // ignored
    }
    
    public void setSoLinger(boolean on, int linger) throws SocketException {
        // ignored
    }
    
    public void setSoTimeout(int t) throws SocketException {
        timeout = t;
    }
    
    public void setTcpNoDelay(boolean on) throws SocketException {
        // ignored
    }
    
    public void setTrafficClass(int tc) throws SocketException {
        // ignored
    }
    
    public void shutdownInput() throws IOException {
        in.close();
    }
    
    public void shutdownOutput() throws IOException {
        out.close();
    }
    
    public String toString() {
        return "HubRoutedVirtualSocket(" + connectionIndex + ")";
    }

    protected final void message(int len, DataInputStream dis) throws IOException {
        // invoker (servicelink) is single threaded, so no need to synchronize
        in.add(len, dis);
    }
  
    protected void sendACK(int data) throws IOException { 
        serviceLink.ackVirtualMessage(connectionIndex, data);
    }
    
    public void flush(byte[] buffer, int off, int len) throws IOException {        
        serviceLink.sendVirtualMessage(connectionIndex, buffer, off, len, 
                timeout);        
    }
   
    protected void setTargetOverload() { 
        gotTargetOverload = true;
    }
    
    protected synchronized void reset(long index) {
        connectionIndex = index;
        gotACK = false;
        waitingForACK = true;
        gotTargetOverload = false;
    }

    protected synchronized int waitForACK(int timeout) {
        
        long deadline = System.currentTimeMillis() + timeout;
        long timeleft = timeout;
        
        while (!gotACK) { 
            
            try { 
                wait(timeleft);
            } catch (InterruptedException e) {
                // ignore
            }
            
            if (!gotACK) { 
                timeleft = deadline - System.currentTimeMillis();
                
                if (timeleft <= 0) { 
                    gotACK = true;
                    ackResult = -1;
                }
            }
        }
        
        waitingForACK = false;
        return ackResult;
    }

    protected synchronized void connectACK(int fragment, int buffer) {
       
        // NOTE: We need to send the ACK ACK from here to prevent a race 
        // condition. It we would do it from HubRouted itself, the user thread
        // may start sending messages (or worse, do a close) before we have 
        // send the ACK ACK!
        
        if (!waitingForACK) { 
            parent.sendAckAck(connectionIndex, false);
            return;
        }
        
        gotACK = true;
        ackResult = 0;
        
        remoteFragmentation = fragment;
        remoteBufferSize = buffer;
        
        out = new HubRoutedOutputStream(this, remoteFragmentation, 
                remoteBufferSize);
        
        in = new HubRoutedInputStream(this, localFragmentation, 
                localBufferSize, localMinimalACKSize); 
       
        notifyAll();
       
        parent.sendAckAck(connectionIndex, true);
    }

    protected synchronized void connectNACK(byte reason) {
        
        if (!waitingForACK) { 
            return;
        }
        
        gotACK = true;
        ackResult = reason;
        
        notifyAll();
    }

    public synchronized boolean connectACKACK(boolean succes) {
        
      //  .println("connectACKACK(" + succes + ")");
        
        if (!waitingForACKACK) { 
            return false;
        }
        
        gotACKACK = true;
        ackACKResult = succes;
        
        notifyAll();
        
        return true;
    }   
    
    protected void messageACK(int data) {
        out.messageACK(data);
    }
}
