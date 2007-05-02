package ibis.smartsockets.virtual;

import ibis.smartsockets.direct.IPAddressSet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;


public class VirtualServerSocket {
    
    private final VirtualSocketFactory parent;
    private int port;
    
    private final LinkedList<VirtualSocket> incoming = 
        new LinkedList<VirtualSocket>();
    
    private int backlog; 
        
    private int timeout = 0;
    private boolean reuseAddress = true;
    private boolean closed = false;
    
    private VirtualSocketAddress localAddress;

    private Map<String, Object> properties;
    
    private boolean bound;
    private int receiveBufferSize = -1;
    
    // Create unbound port
    protected VirtualServerSocket(VirtualSocketFactory parent, 
            Map<String, Object> p) {
        this.parent = parent;
        this.properties = p;        
        this.bound = false;
    }
    
    // Create bound port
    protected VirtualServerSocket(VirtualSocketFactory parent, 
            VirtualSocketAddress address, int port, int backlog, 
            Map<String, Object> p) {
        
        this.parent = parent;
        this.port = port;
        this.backlog = backlog;
        this.localAddress = address;
        this.properties = p;        
        this.bound = true;
    }

    public synchronized int incomingConnection(VirtualSocket s) {  
            
        if (closed) {
            return -1;
        }
        
        if (incoming.size() < backlog) {
            incoming.addLast(s);
            notifyAll();
            return 0;
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
            return 0;
        }
        
        // If not, print an error.....
        System.out.println("Incoming connection on port " 
                + port + " refused: QUEUE FULL (" + incoming.size() + ", " 
                + System.currentTimeMillis() + ")");
        
        return 1;
    }
    
    private synchronized VirtualSocket getConnection() 
        throws SocketTimeoutException { 
     
    //    long time = System.currentTimeMillis();
        
        while (incoming.size() == 0 && !closed) { 
            try { 
                // TODO: this is wrong! USE DEADLINE
            //    System.out.println("Queue empty in accept, waiting for "
            //            + timeout + " ms.");
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
            
           // System.out.println("Accept succesfull after " 
            //        + (System.currentTimeMillis() - time) + " ms. (" 
            //        + incoming.size() + " connections left)");
            
            return incoming.removeFirst();
        } else { 
            
            //System.out.println("Accept failed after " 
             //       + (System.currentTimeMillis() - time) + " ms. (" 
              //      + incoming.size() + " connections left)");
            
            return null;
        }        
    }
    
    public VirtualSocket accept() throws IOException {
        
        VirtualSocket result = null;
        
      //  System.out.println("Starting accept (time = " + System.currentTimeMillis() + ")");
       
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
              //  long time = 0;
                
                try { 
        //            System.out.println("Starting accepting handshake (timeout = " + timeout + ")");
                    
             //       time = System.currentTimeMillis();
                    
                    int t = timeout;
                    
                    if (timeout <= 0) { 
                        t = 1000;
                    }
                    
                    result.connectionAccepted(t);
                    
          //          time = System.currentTimeMillis() - time;
           //         System.out.println("Accepting handshake took " + time + " ms.");
                } catch (IOException e) {
                //    time = System.currentTimeMillis() - time;
                 //   System.out.println("Accepting handshake failed after " + time + " ms.");
                    
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
            incoming.removeFirst().connectionRejected(1000);
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

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public void setProperty(String key, Object val) {
        properties.put(key, val);
    }

    public boolean isBound() {
        return bound;
    }

    public void setReceiveBufferSize(int size) {
        // TODO: Find a way to this in the bind operation ?
        receiveBufferSize = size;
    }

    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        // not implemented
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public int getLocalPort() {
        return port;
    }

    public InetAddress getInetAddress() {
        // TODO Auto-generated method stub
        return null;
    }

    public void bind(SocketAddress endpoint, int backlog) throws IOException {
       
        if (endpoint instanceof VirtualSocketAddress) {
            
            int tmp = ((VirtualSocketAddress) endpoint).port();
            
            parent.bindServerSocket(this, tmp);
            
            this.port = tmp;
            this.localAddress = ((VirtualSocketAddress) endpoint);
        } else { 
            throw new IOException("Unsupported address type");
        }
    }
}
