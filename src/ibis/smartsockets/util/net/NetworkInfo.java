package ibis.smartsockets.util.net;

import java.net.InetAddress;

public class NetworkInfo {

    InetAddress ipv4;
    InetAddress ipv6;
            
    byte [] netmask; 
    byte [] broadcast; 
    byte [] mac;
    
    boolean complete() { 
        return (ipv4 != null || ipv6 != null) && netmask != null 
            && broadcast != null && mac != null;
    }
    
    public String toString() { 
        return "MAC = " + mac + " IPv4 = " + ipv4 + " Mask = " + netmask 
            + " Broadcast = " + broadcast + " IPv6 = " + ipv6; 
    }
}
