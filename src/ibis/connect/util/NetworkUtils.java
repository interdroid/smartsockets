package ibis.connect.util;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * This class contains some network related utilities to deal with IP addresses.
 */
public class NetworkUtils {

    protected static Logger logger = 
        ibis.util.GetLogger.getLogger(NetworkUtils.class.getName());
    
    private NetworkUtils() {
        // No instance allowed 
    }
    
    /**
     * Returns true if the specified address is an external address.
     * External means not a site local, link local, or loopback address.
     * @param address the specified address.
     * @return <code>true</code> if <code>addr</code> is an external address.
     */
    public static boolean isExternalAddress(InetAddress address) {                       
        if (address.isLoopbackAddress()) {
            return false;
        }
        if (address.isLinkLocalAddress()) {
            return false;
        }
        if (address.isSiteLocalAddress()) {
            return false;
        }

        return true;
    }

    /**
     * Returns true if the specified address is a local address.
     * Local means site local, link local, or loopback address.
     * @param address the specified address.
     * @return <code>true</code> if <code>addr</code> is a local address.
     */
    public static boolean isLocalAddress(InetAddress address) {
        return !isExternalAddress(address);
    }
    
    /**
     * Returns true if the array of addresses contains a non-local address. 
     * Local means site local, link local, or loopback address.
     * @param inas the specified addresses.
     * @return <code>true</code> if <code>addr</code> is a local address.
     */    
    public static boolean containsGlobalAddress(InetAddress [] inas) { 
        
        for (int i=0;i<inas.length;i++) { 
            if (isExternalAddress(inas[i])) { 
                return true;
            }            
        }
        
        return false;
    }
    
    /**
     * Adds all the network interfaces found on this machine to the list.
     */    
    private static ArrayList getNetworkInterfacesList() { 
        
        ArrayList list = new ArrayList();        
        Enumeration e = null;

        try {
            e = NetworkInterface.getNetworkInterfaces();                       

            while (e.hasMoreElements()) {                   
                list.add(e.nextElement());
            }        
        } catch (SocketException ex) {
            // logger.debug("Could not get network interfaces.");
        }
        
        return list;
    }
    
    /**
     * Returns all the network interfaces found on this machine.
     * 
     * @return the network interfaces.
     */
    public static NetworkInterface [] getNetworkInterfaces() { 
        ArrayList list = getNetworkInterfacesList();       
        
        return (NetworkInterface []) list.toArray(
                    new NetworkInterface[list.size()]);
    }
    
    /**
     * Adds all IP addresses that are bound to a specific network interface to 
     * a list. If desired, loopback and/or IPv6 addresses can be ignored.  
     * 
     * @param nw the network interface for which the addresses are determined
     * @param target the list used to store the addresses
     * @param ignoreLoopback ignore loopback addresses 
     * @param ignoreIP6 ignore IPv6 addresses
     */
    private static void getAllHostAddresses(NetworkInterface nw, List target, 
            boolean ignoreLoopback, boolean ignoreIP6) { 
                
        logger.info("   " + nw.getDisplayName() + ":");
                        
        Enumeration e2 = nw.getInetAddresses();
            
        while (e2.hasMoreElements()) {
            
            InetAddress tmp = (InetAddress) e2.nextElement();
                       
            boolean t1 = !ignoreLoopback || !tmp.isLoopbackAddress();
            boolean t2 = !ignoreIP6 || (tmp instanceof Inet4Address); 
            
            if (t1 && t2) {
                logger.info("    - " + tmp.getHostAddress() + " (used)");                
                target.add(tmp);
            } else { 
                logger.info("    - " + tmp.getHostAddress() + " (ignored)");
            }
        }
    }
            
    /**
     * Returns all IP addresses that could be found on this machine.
     * 
     * @return all IP addresses found.
     */
    public static InetAddress [] getAllHostAddresses() {
        return getAllHostAddresses(false, false);
    }

    /**
     * Returns all IP addresses that could be found on this machine. 
     * If desired, loopback and/or IPv6 addresses can be ignored.  
     * 
     * @param ignoreLoopback ignore loopback addresses 
     * @param ignoreIP6 ignore IPv6 addresses
     * 
     * @return all IP addresses found that adhere to the restrictions.
     */
    public static InetAddress [] getAllHostAddresses(boolean ignoreLoopback, 
            boolean ignoreIP6) { 

        ArrayList nwl = getNetworkInterfacesList();
        ArrayList list = new ArrayList();
            
        logger.info("Determining available addresses:");
        
        for (int i=0;i<nwl.size();i++) {             
            NetworkInterface nwi = (NetworkInterface) nwl.get(i);
            getAllHostAddresses(nwi, list, ignoreLoopback, ignoreIP6);
        } 
        
        return (InetAddress []) list.toArray(new InetAddress[list.size()]);
    }
    
    
    /**
     * Returns all IP addresses that are bound to a specific network interface.
     * 
     * @param nw the network interface for which the addresses are determined 
     * @return all IP address found.
     */
    public static InetAddress [] getAllHostAddresses(NetworkInterface nw) { 
        return getAllHostAddresses(nw, false, false);
    }
    
    /**
     * Returns all IP addresses that are bound to a specific network interface.
     * If desired, loopback and/or IPv6 addresses can be ignored.  
     * 
     * @param nw the network interface for which the addresses are determined
     * @param ignoreLoopback ignore loopback addresses 
     * @param ignoreIP6 ignore IPv6 addresses
     * 
     * @return all IP addresses found that adhere to the restrictions.
     */
    public static InetAddress [] getAllHostAddresses(NetworkInterface nw, 
            boolean ignoreLoopback, boolean ignoreIP6) { 

        // Return the addresses that are bound to the given interface.                
        ArrayList list = new ArrayList();
        getAllHostAddresses(nw, list, ignoreLoopback, ignoreIP6);
        return (InetAddress []) list.toArray(new InetAddress[list.size()]);
    }
       
    /**
     * Converts a byte representation of an IP address to a String. 
     * 
     * @param bytes the address to convert
     * @return the String representation of the address.
     */
    public static String bytesToString(byte [] bytes) { 
        
        StringBuffer result = new StringBuffer("");
        
        for (int i=0;i<bytes.length;i++) { 
            result.append(bytes[i] & 0xff);
            
            if (i != bytes.length-1) { 
                result.append(".");
            }
        }
        
        return result.toString();        
    }
        
    /**
     * Converts a IP address to a String. 
     * 
     * @param ad the address to convert
     * @return the String representation of the address.
     */
    public static String ipToString(InetAddress ad) { 
        
        return ad.getHostAddress(); 
        
        /*
        
        byte [] bytes = ad.getAddress();
        
        StringBuffer result = new StringBuffer("");
        
        for (int i=0;i<bytes.length;i++) { 
            result.append(bytes[i] & 0xff);
            
            if (i != bytes.length-1) { 
                result.append(".");
            }
        }
        
        return result.toString();
        */        
    }
    
    /**
     * Converts an array of IP addresses to a String. 
     * 
     * @param ads the addresses to convert
     * @return the String representation of the addresses.
     */    
    public static String ipToString(InetAddress [] ads) { 
                
        StringBuffer result = new StringBuffer("[");
        
        for (int i=0;i<ads.length;i++) { 
            result.append(ipToString(ads[i]));
            
            if (i != ads.length-1) { 
                result.append(", ");
            }
        }
        
        result.append("]");
        
        return result.toString();        
    }

    public static boolean matchAddress(byte [] ad, byte [] sub, byte [] mask) { 
        
        if (sub.length != ad.length) {
            // Not sure how to mix IPv4 and IPv6 yet ...
            return false;
        }
        
        for (int i=0;i<sub.length;i++) { 
            
            if ((ad[i] & mask[i]) != (sub[i] & mask[i])) { 
                return false;
            }
        }

        return true;
    }
    
    public static boolean matchAddress(InetAddress ad, byte [] sub, 
            byte [] mask) {         
        return matchAddress(ad.getAddress(), sub, mask);
    }
    
    public static String getHostname() throws IOException {         

        InetAddress a = InetAddress.getLocalHost();
            
        if (a != null) {                    
            return a.getHostName();
        } else { 
            throw new IOException("Failed to get local address");
        }
    }
    
    
    
    /*
    
    
    
    
    
    
    public static InetAddress canConnectFrom(InetAddress adr) { 
        
        // Find the local address that is most likely to be able to connect to 
        // the given address.
        return canConnectFrom(adr, false); 
    }

    private static int getDistance(InetAddress remote, InetAddress local) { 
        
        if (remote.equals(local)) {                 
            return 0;
        } 
        
        byte [] remoteIP = remote.getAddress();
        byte [] localIP = local.getAddress();
        
        if (remoteIP.length != localIP.length) {
            // For now don't mix IPv4 and IPv6
            return Integer.MAX_VALUE;
        }
    
        int distance = remoteIP.length;
        
        for (int i=0;i<remoteIP.length-1;i++) { 
            if (remoteIP[i] == localIP[i]) { 
                distance--;
            } else {                 
                // check if we founf any similarities 
                if (distance != remoteIP.length) {
                    return distance;
                } 
                
                // no similarities. Return a score based on the type of the 
                // local address. This ensures that is no similarities are found 
                // at all, the addresses will stil be sorted in a decent order 
                // [public, sitelocal, linklocal, loopback]
                    
                if (local.isLoopbackAddress()) {
                    return Integer.MAX_VALUE;
                }
                if (local.isLinkLocalAddress()) {
                    return Integer.MAX_VALUE/2;
                }
                
                if (local.isSiteLocalAddress()) {
                    return Integer.MAX_VALUE/3;
                }
                
                // else: public address
                return Integer.MAX_VALUE/4;
            }
        }    
        
        return distance;
    }
    
    public static InetAddress [] possibleConnections(InetAddress adr) {
    
        // Find the local address that is most likely to be able to connect to 
        // the given address. The mayTry parameter specifies if this method is 
        // allowed to create a connection to 'try' if it works...
        
        InetAddress [] ias = getAllHostAddresses();
                    
        int [] distance = new int[ias.length];
        Arrays.fill(distance, Integer.MAX_VALUE); 
        
        for (int i=0;i<ias.length;i++) {         
            distance[i] = getDistance(adr, ias[i]);
        }
        
        // simple bubble sort 
        for (int i=0;i<ias.length-1;i++) { 
         
            int best = i;
            
            for (int j=i+1;j<ias.length;j++) {                 
                if (distance[j] < distance[best]) { 
                    best = j;
                }
            }
            
            if (best != i) { 
                // found a better one, so swap
                InetAddress t1 = ias[i];
                ias[i] = ias[best];
                ias[best] = t1;
                
                int t2 = distance[i];
                distance[i] = distance[best];
                distance[best] = t2;                                
            }            
        }    
                       
        return ias;        
    } 
        
    public static InetAddress canConnectFrom(InetAddress adr, boolean mayTry) { 
        
        // Find the local address that is most likely to be able to connect to 
        // the given address. The mayTry parameter specifies if this method is 
        // allowed to create a connection to 'try' if it works...
        
        InetAddress [] ias = getAllHostAddresses();
            
        InetAddress best = null; 
        int distance = Integer.MAX_VALUE;
        
        for (int i=0;i<ias.length;i++) { 
        
            int dist = getDistance(adr, ias[i]);
                
            if (dist < distance) { 
                distance = dist;
                best = ias[i];
            }
        }
        
        if (distance != Integer.MAX_VALUE) { 
            return best;
        } else { 
            return null;
        }
    }
    
    */
      
}
