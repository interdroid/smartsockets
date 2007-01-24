package smartsockets.virtual;


import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

import smartsockets.direct.IPAddressSet;

public class VirtualServerSocket {
    
    private final VirtualSocketFactory parent;
    private final int port;
    
    private final LinkedList<VirtualSocket> incoming = 
        new LinkedList<VirtualSocket>();
    
    private final int backlog; 
        
    private int timeout = 0;
    private boolean reuseAddress = true;
    private boolean closed = false;
    
    private VirtualSocketAddress localAddress;

    private Map properties;
    
    protected VirtualServerSocket(VirtualSocketFactory parent, 
            VirtualSocketAddress address, int port, int backlog, 
            Map p) {
        
        this.parent = parent;
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
            incoming.addLast(s);
            notifyAll();
            return true;
        }

        // Try so remove all closed sockets from the queue ...
        ListIterator<VirtualSocket> itt = incoming.listIterator();
        
        while (itt.hasNext()) { 
            VirtualSocket v = itt.next();
            
            if (v.isClosed()) { 
                itt.remove();
            }
        }
 
        // See if there is room now... 
        if (incoming.size() < backlog) {
            incoming.addLast(s);
            notifyAll();
            return true;
        }

        // If not, print an error.....
        System.out.println("Incoming connection on port " 
                + port + " refused: QUEUE FULL (" + incoming.size() + ")");
        
        return false;
    }
    
    private synchronized VirtualSocket getConnection() 
        throws SocketTimeoutException { 
     
        while (incoming.size() == 0 && !closed) { 
            try { 
                // TODO: this is wrong! USE DEADLINE
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
            return incoming.removeFirst();
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
            } else if (result.isClosed()) { 
                // Check is the other side is already closed...
                result = null;
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
        
        result.setTcpNoDelay(true);        
        return result;
    }
        
    public synchronized void close() throws IOException {
        
        closed = true;       
        notifyAll();   // wakes up any waiting accept
        
        while (incoming.size() != 0) {
            incoming.removeFirst().connectionRejected();
        } 
        
        parent.closed(port);            
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
