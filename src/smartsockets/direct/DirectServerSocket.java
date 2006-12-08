package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

public class DirectServerSocket {

    protected static final byte ACCEPT = 47;
    protected static final byte WRONG_MACHINE = 48;
    protected static final byte FIREWALL_REFUSED = 49;
    
    // The real server socket
    private final ServerSocket serverSocket; 
        
    // This is the local address, i.e., the SocketAddress that uses IP's + ports
    // that are directly found on this machine.    
    private final SocketAddressSet local;
    
    // This is the initial message which contains the local address in coded 
    // form. The first two bytes contain the size of the address..
    private final byte [] handShake;
    
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
    
    // The network preferences that this server socket should take into account.
    private NetworkPreference preference;
    
    protected DirectServerSocket(SocketAddressSet local, ServerSocket ss, 
            NetworkPreference preference) {
        
        /*super(null);*/
        this.local = local;
        this.serverSocket = ss;
        
        byte [] tmp = local.getAddress();
        
        handShake = new byte[2 + tmp.length];
        handShake[0] = (byte) (tmp.length & 0xFF);
        handShake[1] = (byte) ((tmp.length >> 8) & 0xFF);
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
             
    private void doClose(Socket s, InputStream in, OutputStream out) { 
        try { 
            in.close();
        } catch (Exception e) {
            // ignore
        }
        try { 
            out.close();
        } catch (Exception e) {
            // ignore
        }
        
        try { 
            s.close();
        } catch (Exception e) {
            // ignore
        }
    }
    
    public DirectSocket accept() throws IOException {    
        
        DirectSocket result = null;
        
        while (result == null) { 
            
            // Note: may result in timeout, which is OK.
            Socket s = serverSocket.accept();
            
            InputStream in = null;
            OutputStream out = null;
            
            try { 
                s.setSoTimeout(10000);
                s.setTcpNoDelay(true);
                
                // Start by sending our address to the client. It will check for
                // itself if we are the expected target machine.
                out = s.getOutputStream();
                out.write(handShake);
                out.flush();
                
                // Next, read the address of the client (no port numbers, just 
                // addresses. We use this to check if the machine is allowed to 
                // connected to us according to the firewall rules.
                in = s.getInputStream();
              
                // Read the size of the machines address blob
                int size = (in.read() & 0xFF) | ((in.read() & 0xFF) << 8); 
                
                // Read the bytes....
                byte [] tmp = new byte[size];
                
                int off = 0; 
                
                while (off < size) { 
                    off += in.read(tmp, off, size-off);
                }
                
                // Translate into an address
                IPAddressSet ads = IPAddressSet.getByAddress(tmp);
                
                // Check if the connection is acceptable and write the 
                // appropriate opcode into the stream.
                if (preference.accept(ads.addresses)) { 
                        
                    out.write(ACCEPT);
                    out.flush();       
    
                    // Very unfortunate that this is synchronous.....   
                    int opcode = in.read();
                    
                    if (opcode == ACCEPT) { 
                        s.setSoTimeout(0);
                        result = new DirectSocket(s, in, out);
                    } else { 
                         doClose(s, in, out);
                    }     
                    
                } else { 
                    
                    System.out.println("Firewall refused connection from: " + ads);
                  
                    out.write(FIREWALL_REFUSED);
                    out.flush();
                    
                    // TODO: do we really need to wait for incoming byte here ??
                    in.read();
                    doClose(s, in, out);
                }
                
                               
            } catch (IOException ie) { 
                doClose(s, in, out);
            }
        }
        
        return result;        
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
