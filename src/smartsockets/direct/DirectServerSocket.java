package smartsockets.direct;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

public class DirectServerSocket {

    // The real server socket
    private ServerSocket serverSocket; 
        
    // This is the local address, i.e., the SocketAddress that uses IP's + ports
    // that are directly found on this machine.    
    private SocketAddressSet local;
    
    // These are the external addresses, i.e., SocketAddresses which use IP's         
    // which cannot be found on this machine, but which can still be used to 
    // reach this ServerSocket. When a machine is behind a NAT box, for example, 
    // it may be reachable using the external IP of the NAT box (when it it 
    // using port forwarding). The server socket may also be on the receiving 
    // end of an SSH tunnel, reachable thru some proxy, etc. 
    //
    // Note that the IP's in the external addresses are not bound to a local 
    // network.     
    private SocketAddressSet external;
    
    protected DirectServerSocket(SocketAddressSet local, ServerSocket ss) {
        /*super(null);*/
        this.local = local;
        this.serverSocket = ss;
    }
               
    /**
     * This method adds an external address to a ServerSocket. 
     * 
     * External addresses are addresses which can be used to reach the server 
     * socket, but which are not bound to local network hardware (i.e., NAT port 
     * forwarding, SSH tunnels, etc). 
     * 
     * @param address the external address to add. 
     */
    protected void addExternalAddress(SocketAddressSet address) {        
        // TODO: some checks on the address to see if it makes sence ? 
        
        // Create array if it doesn't exist yet. 
        if (external == null) {
            external = address;            
        } else { 
            external = SocketAddressSet.merge(external, address);
        }
    }
             
    public DirectSocket accept() throws IOException {            
        return new DirectSocket(serverSocket.accept());        
    }

    public void close() throws IOException {
        serverSocket.close();
    }

    public boolean isClosed() {
        return serverSocket.isClosed();        
    }
   
    public ServerSocketChannel getChannel() {
        return serverSocket.getChannel();
    }
       
    public SocketAddressSet getAddressSet() {
        if (external != null) { 
            return SocketAddressSet.merge(local, external);            
        } else { 
            return local;
        }
    }
    
    public SocketAddressSet getLocalAddressSet() {       
        return local;
    }
    
    public SocketAddressSet getExternalAddressSet() {       
        return external;
    }
        
    public synchronized int getSoTimeout() throws IOException {
        return serverSocket.getSoTimeout();
    }

    public synchronized void setSoTimeout(int timeout) throws SocketException {
        serverSocket.setSoTimeout(timeout);
    }
          
    public boolean getReuseAddress() throws SocketException {
        return serverSocket.getReuseAddress();
    }

    public void setReuseAddress(boolean on) throws SocketException {
        serverSocket.setReuseAddress(on);
    }

    public String toString() {        
        return "DirectServerSocket(" + local + ", " + external + ")";  
    }
}
