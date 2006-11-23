package smartsockets.virtual.modules.hubrouted;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Map;

import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.virtual.VirtualSocket;
import smartsockets.virtual.VirtualSocketAddress;

public class HubRoutedVirtualSocket extends VirtualSocket {
    
    private final HubRouted parent;     
    private final ServiceLink serviceLink;
    private final long connectionIndex;
        
    private int timeout = 0;
    
    private boolean closed = false;
    
    private final HubRoutedOutputStream out;
    private final HubRoutedInputStream in;
    
    private final LinkedList<byte[]> incoming = new LinkedList<byte[]>();
    
    private boolean closeInPending = false;

    protected HubRoutedVirtualSocket(HubRouted parent, 
            VirtualSocketAddress target, ServiceLink serviceLink, 
            long connectionIndex, Map p) {        
        
        super(target);
        
        this.parent = parent;
        this.serviceLink = serviceLink;
        this.connectionIndex = connectionIndex;
    
        this.out = new HubRoutedOutputStream(this, 16*1024);
        this.in = new HubRoutedInputStream(this);            
    }
   
    
    protected void connectionAccepted() throws IOException { 
        
        if (!serviceLink.acceptIncomingConnection(connectionIndex)) { 
            // oops, we are too late!
            close();
        }
    }
    
    public void connectionRejected() { 
        serviceLink.rejectIncomingConnection(connectionIndex);         
    }
    
    public void waitForAccept() throws IOException {
        
        /*
        try { 
            int result = in.read();
        
            switch (result) {
            case Direct.ACCEPT:
                // TODO: find decent port here ?
                return;
                
            case Direct.PORT_NOT_FOUND:
                throw new SocketException("Remote port not found");                
                
            case Direct.WRONG_MACHINE:            
                throw new SocketException("Connection ended up on wrong machine!");
                
            case Direct.CONNECTION_REJECTED:
                throw new SocketException("Connection rejected");
                
            default:
                throw new SocketException("Got unknown reply during connect!");
            }
            
        } catch (IOException e) {
            // This module worked fine, but we got a 'normal' exception while 
            // connecting (i.e., because the other side refused to connection). 
            // There is no use trying other modules.
            DirectSocketFactory.close(s, out, in);
            throw e;
        } */
        
    }
    
    public void close() {
        
        synchronized (this) { 
            if (closed) { 
                return;
            }
            
            closed = true;        
        
            in.closePending();
           
            // Wakeup any thread blocked on a read....
            notifyAll();
        }
        
        
        try {
            out.close();
        } catch (Exception e) { 
            // ignore
        }
        
        try {
            serviceLink.closeVirtualConnection(connectionIndex);
        } catch (Exception e) {
            // TODO: handle exception!            
        }

        parent.close(connectionIndex);        
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

    public synchronized void message(byte[] data) {
        incoming.addLast(data);
        notifyAll();
    }
    
    // TODO: addtimeout!!!!
    private synchronized byte [] getBuffer() { 
        
        while (incoming.size() == 0) {
            
            if (closed) { 
                return null;
            }
            
            try { 
               // System.out.println("Socket " + connectionIndex 
               //         + " blocks for messages!!");
                wait(1000);                
            } catch (Exception e) {
                // ignore
            }
        }
        
        return incoming.removeFirst();        
    }
    
    public void flush(byte[] buffer, int off, int len) throws IOException {        
        serviceLink.sendVirtualMessage(connectionIndex, buffer, off, len, 
                timeout);        
    }

    public byte[] getBuffer(byte[] buffer) throws IOException {
        
        if (buffer != null) {
            // Ack a previous buffer 
            serviceLink.ackVirtualMessage(connectionIndex, buffer);
        }

        return getBuffer(); 
    }   
}
