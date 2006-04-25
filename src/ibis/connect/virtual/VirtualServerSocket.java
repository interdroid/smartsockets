package ibis.connect.virtual;

import ibis.connect.direct.IPAddressSet;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.Map;

public class VirtualServerSocket {
    
    private final int port;
    
    private final LinkedList incoming = new LinkedList();
    private final int backlog; 
        
    private int timeout = 0;
    private boolean reuseAddress = true;
    private boolean closed = false;
    
    private VirtualSocketAddress localAddress;

    private Map properties;
    
    protected VirtualServerSocket(VirtualSocketAddress address, int port,
            int backlog, Map p) {
        
        this.port = port;
        this.backlog = backlog;
        this.localAddress = address;
        this.properties = p;        
    }

    public synchronized boolean incomingConnection(VirtualSocket s) {  
                
        if (closed) { 
            return false;
        }
        
        if (incoming.size() < backlog) {
            incoming.add(s);
            notifyAll();
            return true;
        }
        
        return false;
    }
    
    private synchronized VirtualSocket getConnection() 
        throws SocketTimeoutException { 
     
        while (incoming.size() == 0 && !closed) { 
            try { 
                wait(timeout);                
            } catch (Exception e) {
                // ignore
            }         
            
            // Check if our wait time has expired. 
            if (timeout > 0 && incoming.size() == 0 && !closed) { 
                throw new SocketTimeoutException("Time out during accept");
            }           
        }
     
        if (incoming.size() > 0) { 
            return (VirtualSocket) incoming.removeFirst();
        } else { 
            return null;
        }        
    }
    
    public VirtualSocket accept() throws IOException {
        
        VirtualSocket result = null;
        
        while (result == null) { 
            result = getConnection();
            
            if (result == null) { 
                // Can only happen if socket has been closed
                throw new IOException("Socket closed during accept");                
            } else {
                // See if the other side is still willing to connect ...                
                try { 
                    result.connectionAccepted();
                } catch (IOException e) {
                    VirtualSocketFactory.logger.info("VirtualServerPort( " 
                            + port + ") got exception during accept!", e);
                    result = null;                    
                }
            }
        }
        
        return result;
    }
        
    public synchronized void close() throws IOException {
        
        closed = true;       
        notifyAll();   // wakes up any waiting accept
        
        while (incoming.size() != 0) {
            VirtualSocket s = (VirtualSocket) incoming.removeFirst();
            s.connectionRejected();
        } 
        
        VirtualSocketFactory.factory.closed(port);            
    }

    public int getPort() { 
        return port;
    }
    
    public boolean isClosed() {
        return closed;
    }
   
    public ServerSocketChannel getChannel() {
        throw new RuntimeException("operation not implemented by " + this);
    }

    public IPAddressSet getIbisInetAddress() {
        throw new RuntimeException("operation not implemented by " + this);
    }

    public VirtualSocketAddress getLocalSocketAddress() {        
        return localAddress;
    }
       
    public int getSoTimeout() throws IOException {
        return timeout;
    }

    public void setSoTimeout(int t) throws SocketException {
        timeout = t;
    }
          
    public boolean getReuseAddress() throws SocketException {
        return reuseAddress;
    }

    public void setReuseAddress(boolean v) throws SocketException {
        reuseAddress = v;
    }

    public String toString() {
        return "VirtualServerSocket(" + localAddress.toString() + ")";
    }
    
    public Map properties() {
        return properties;
    }

    public void setProperties(Map properties) {
        this.properties = properties;
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public void setProperty(String key, Object val) {
        properties.put(key, val);
    }
}
