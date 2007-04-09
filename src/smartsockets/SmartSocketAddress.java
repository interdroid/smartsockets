package smartsockets;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import smartsockets.virtual.VirtualSocketAddress;

public class SmartSocketAddress {

    public static SocketAddress create(String hostport, boolean smart) 
        throws UnknownHostException { 
        
        // First see if InetSocketAddress understands 'hostport'. If so, there 
        // is no need using a VirtualSocketAddress.
        if (!smart) { 
            int index = hostport.indexOf(':');
            
            if (index == -1) { 
                throw new IllegalArgumentException("String does not contain a "
                        + "InetSocketAddress!");
            }
            
            try { 
                int port = Integer.parseInt(hostport.substring(index+1));
                return new InetSocketAddress(hostport.substring(0, index), port);
            } catch (Exception e) {
                throw new IllegalArgumentException("String does not contain a "
                        + "InetSocketAddress! - cannot parse port!", e);
            }
        } else { 
            return new VirtualSocketAddress(hostport);
        }
    }
    
    public static SocketAddress create(String host, int port, boolean smart) 
        throws UnknownHostException { 
        
        // First see if InetSocketAddress understands 'host'. If so, there 
        // is no need using a VirtualSocketAddress.
        if (!smart) { 
            return new InetSocketAddress(host, port);
        } else { 
            return new VirtualSocketAddress(host, port);
        }
    }
}
