package smartsockets.direct;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import smartsockets.util.NetworkUtils;

public final class Network {

    final byte[] network;
    final byte[] mask;

    Network(byte[] network, byte[] mask) {
        this.network = network;
        this.mask = mask;
    }
    
    boolean match(InetAddress addr) { 
        return NetworkUtils.matchAddress(addr, network, mask);
    }
    
    boolean match(InetAddress [] addr) {
        
        for (int i=0;i<addr.length;i++) { 
            if (match(addr[i])) { 
                return true;
            }
        }
        
        return false;
    }
    
    boolean match(InetSocketAddress [] addr) {
        
        for (int i=0;i<addr.length;i++) { 
            if (match(addr[i].getAddress())) { 
                return true;
            }
        }
        
        return false;
    }    
    
    public String toString() { 
        return NetworkUtils.bytesToString(network)
            + "/" + NetworkUtils.bytesToString(mask);
    }
}