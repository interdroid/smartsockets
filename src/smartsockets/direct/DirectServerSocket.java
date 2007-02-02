package smartsockets.direct;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

public class DirectServerSocket {

    protected static final byte TYPE_SERVER               = 7;
    protected static final byte TYPE_SERVER_WITH_FIREWALL = 8;
    protected static final byte TYPE_CLIENT_CHECK         = 9;
    protected static final byte TYPE_CLIENT_NOCHECK       = 10;
    
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
    
    // This is the initial message which contains the local address in coded 
    // form. The first two bytes contain the size of the address..
    private final byte [] altHandShake;
    
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
    private final NetworkPreference preference;
    private final boolean haveFirewallRules;
    
    protected DirectServerSocket(SocketAddressSet local, ServerSocket ss, 
            NetworkPreference preference) {
        
        /*super(null);*/
        this.local = local;
        this.serverSocket = ss;
        this.preference = preference;
        
        byte [] tmp = local.getAddress();
        
        handShake = new byte[2 + tmp.length];
       
        handShake[0] = (byte) (tmp.length & 0xFF);
        handShake[1] = (byte) ((tmp.length >> 8) & 0xFF);
        System.arraycopy(tmp, 0, handShake, 2, tmp.length);        

        altHandShake = DirectSocketFactory.toBytes(5, local.getAddressSet(), 2);
        
        if (preference != null && preference.haveFirewallRules()) { 
            haveFirewallRules = true;     
            altHandShake[0] = TYPE_SERVER_WITH_FIREWALL;            
        } else { 
            haveFirewallRules = false;            
            altHandShake[0] = TYPE_SERVER;               
        }
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
        
        DirectSimpleSocket result = null;
    
        // Move up ?
        byte [] userIn = new byte[4];
        
        while (result == null) { 
            
            // Note: may result in timeout, which is OK.
            Socket s = serverSocket.accept();
            
            InputStream in = null;
            OutputStream out = null;

/* BELOW IS THE 'MATHIJS/NIELS VERSION                
             
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
                int size = (in.read() & 0xFF);
                size |= ((in.read() & 0xFF) << 8); 
                     
                // Read the bytes....
                byte [] tmp = new byte[size];
                
                int off = 0; 
                
                while (off < size) { 
                    off += in.read(tmp, off, size-off);
                }
                
                // If available, read the network name of the client
                size = (in.read() & 0xFF);
                size |= ((in.read() & 0xFF) << 8); 
               
                String name = null;
                
                if (size > 0) { 
                    byte [] tmp2 = new byte[size];
                    
                    int off2 = 0; 
                    
                    while (off2 < size) { 
                        off2 += in.read(tmp2, off2, size-off2);
                    }     
                    
                    name = new String(tmp2);
                }
                
                // Translate into an address
                IPAddressSet ads = IPAddressSet.getByAddress(tmp);
                
                // Check if the connection is acceptable and write the 
                // appropriate opcode into the stream.
             
                if (preference.accept(ads.addresses, name)) { 
                    out.write(ACCEPT);
                    out.flush();       
    
                    // Very unfortunate that this is synchronous.....   
                    int opcode = in.read();
                    
                    if (opcode == ACCEPT) { 
                        s.setSoTimeout(0);
                        
                        // TODO: fix to get 'real' port numbers here... 
                        result = new DirectSimpleSocket(local, 
                                SocketAddressSet.getByAddress(ads, 1, null), 
                                in, out, s);
                    } else { 
                         doClose(s, in, out);
                    }     
                    
                } else { 
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
*/
                // THIS IS THE HPDC VERSION
                
            try { 
                s.setSoTimeout(10000);
                s.setTcpNoDelay(true);
                
                // Start by sending our type and address to the client. It will 
                // check for itself if we are the expected target machine.
                out = s.getOutputStream();
                out.write(altHandShake);
                //out.write(networkNameInBytes);
                out.flush();
            
                in = s.getInputStream();
                
                // Read the type of the client (should always be TYPE_CLIENT_*)
                int type = in.read();
                
                // Read the user data
                int off = 0; 
                    
                while (off < 4) { 
                    off += in.read(userIn, off, 4-off);
                }
                
                // Read the size of the machines address blob
                int size = (in.read() & 0xFF);
                size |= ((in.read() & 0xFF) << 8); 
                     
                // Read the bytes....
                byte [] tmp = new byte[size];
                
                off = 0; 
                
                while (off < size) { 
                    off += in.read(tmp, off, size-off);
                }
                
                // Read the size of the network name
                size = (in.read() & 0xFF);
                size |= ((in.read() & 0xFF) << 8); 

                // Read the address itself....
                byte [] name = new byte[size];

                off = 0; 

                while (off < size) { 
                    off += in.read(name, off, size-off);
                }                
            
                IPAddressSet a = IPAddressSet.getByAddress(tmp);
                SocketAddressSet sa = SocketAddressSet.getByAddress(a, 1, null); 
                
                // Optimistically create the socket ? 
                // TODO: fix to get 'real' port numbers here... 
                result = new DirectSimpleSocket(local, sa, in, out, s);
                
                int userData = (((userIn[0] & 0xff) << 24) | 
                        ((userIn[1] & 0xff) << 16) |
                        ((userIn[2] & 0xff) << 8) | 
                        (userIn[3] & 0xff));
                    
                result.setUserData(userData);
                
                if (haveFirewallRules) { 
                    
                    String network = new String(name);
                    
                    // We must check if we are allowed to accept the client
                    if (preference.accept(a.addresses, network)) { 
                        out.write(ACCEPT);
                        out.flush();       
                    } else { 
                        out.write(FIREWALL_REFUSED);
                        out.flush();
                        
                        // TODO: do we really need to wait for incoming byte here ??
                        in.read();
                        doClose(s, in, out);
                        result = null;
                        continue; // TODO: refactor!!!
                    }                    
                }
                
                if (type == TYPE_CLIENT_CHECK) { 
                    
                    // Read if the client accept us. 
                    int opcode = in.read();
                
                    if (opcode != ACCEPT) { 
                        doClose(s, in, out);
                        result = null;
                    }
                }

                s.setSoTimeout(0);
               
            } catch (IOException ie) {
                doClose(s, in, out);
                result = null;
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

    public void setReceiveBufferSize(int size) throws SocketException {
        serverSocket.setReceiveBufferSize(size);
    }

    public void setReuseAddress(boolean on) throws SocketException {
        serverSocket.setReuseAddress(on);
    }

    public String toString() {        
        return "DirectServerSocket(" + local + ", " + external + ")";  
    }
}
