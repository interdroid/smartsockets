package smartsockets.util;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.apache.log4j.Logger;

import smartsockets.util.net.NativeNetworkConfig;

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
    
    /**
     * Converts a String containing a MAC address to bytes. 
     * A 6 byte MAC address using single character seperators is expected as 
     * input.    
     * 
     * @param mac the address to convert
     * @return the byte [] representation of the address.
     */        
    public static byte [] MACStringToBytes(String mac) { 
        
        byte [] result = new byte[6];
        
        StringBuffer tmp = new StringBuffer(mac); 
        
        for (int i=0;i<6;i++) {             
            result[i] = 
                (byte) (0xff & Integer.parseInt(tmp.substring(i*3, i*3+2), 16));            
        }
        
        return result;
    }
    
    /**
     * Converts an array of bytes to a String. The byte values are sepated 
     * by a user-specified seperator. If required, the bytes are printed in 
     * hexadecimal form.  
      *
     * @param b the bytes to convert
     * @param hex specifies if a hexadecimal format is required.     
     * @param sep the seperator to use
     *  
     * @return the String representation of the address.
     */        
    private static String bytesToString(byte [] b, boolean hex, char sep) { 
     
        StringBuffer tmp = new StringBuffer(); 
        
        for (int i=0;i<b.length;i++) {             
            if (hex) {             
                int value = 0xff & b[i]; 
                
                if (value <= 0xf) {
                    tmp.append('0');
                }
                
                tmp.append(Integer.toHexString(value));
            } else { 
                tmp.append(0xff & b[i]);
            }
            
            if (i != b.length-1) { 
                tmp.append(sep);
            } 
        }
        
        return tmp.toString();
    }
    
    /**
     * Converts an array of bytes containing a MAC address to a String. 
     * A 6 byte MAC address is expected as input. A colon will be used as a 
     * seperator.    
     * 
     * @param mac the address to convert
     * @return the String representation of the address.
     */        
    public static String MACToString(byte [] mac) {
        return bytesToString(mac, true, ':');
    }
    
    /**
     * Converts an array of bytes containing a IPv4 address, IPv4 broadcast 
     * address or a netmask to a String. 
     * 
     * @param ipv4 the array to convert
     * @return the String representation of the address or netmask.
     */        
    public static String ipv4ToString(byte [] ipv4) {
        return bytesToString(ipv4, false, '.');
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
    
    public static String getMACAddressAsString(InetAddress ip) throws IOException {         
        return MACToString(getMACAddress(ip));
    }
    
    public static String getNetmaskAsString(InetAddress ip) throws IOException { 
        return ipv4ToString(getNetmask(ip));
    }
    
    public static String getBroadAsString(InetAddress ip) throws IOException { 
        return ipv4ToString(getBroadcast(ip));
    }
    
    public static byte [] getMACAddress(InetAddress ip) throws IOException { 
        return NativeNetworkConfig.getMACAddress(ip);
    }

    public static byte [] getNetmask(InetAddress ip) throws IOException { 
        return NativeNetworkConfig.getNetmask(ip);
    }
    
    public static byte [] getBroadcast(InetAddress ip) throws IOException { 
        return NativeNetworkConfig.getBroadcast(ip);
    }
    
    
    public static void main(String [] args) { 
        
        ArrayList nwl = getNetworkInterfacesList();
        
        for (int i=0;i<nwl.size();i++) {             
            NetworkInterface nwi = (NetworkInterface) nwl.get(i);
            
            System.out.println("Found network interface: " 
                    + nwi.getDisplayName()); 
            
            ArrayList ips = new ArrayList(); 
            
            getAllHostAddresses(nwi, ips, false, true);
            
            String mac = null;
            
            for (int j=0;j<ips.size();j++) {               
                try { 
                    mac = getMACAddressAsString((InetAddress) ips.get(j));
                } catch (Exception e) {
                    // ignore
                }
                
                if (mac != null) { 
                    break;
                }
            }

            System.out.println("  MAC Address    : " + (mac != null ? mac : "unknown"));  
                       
            for (int j=0;j<ips.size();j++) {

                InetAddress ip = (InetAddress) ips.get(j);
                
                if (ip instanceof Inet4Address) { 
                
                    String netmask = null;
                
                    try { 
                        netmask = getNetmaskAsString(ip);
                    } catch (Exception e) {
                        // ignore
                    }
                
                    String broadcast = null;
                
                    try { 
                        broadcast = getBroadAsString(ip);
                    } catch (Exception e) {
                        // ignore
                    }
                    
                    System.out.println("  IPv4 Address   : " + ipToString(ip));
                    System.out.println("       Netmask   : " + (netmask != null ? netmask : "unknown"));
                    System.out.println("       Broadcast : " + (broadcast != null ? broadcast : "unknown"));
                } else { 
                    System.out.println("  IPv6 Address   : " + ipToString(ip)); 
                }
                
                String type = "global";
                
                if (ip.isLoopbackAddress()) { 
                    type = "loopback";
                } else if (ip.isLinkLocalAddress()) { 
                    type = "link local";                        
                } else if (ip.isSiteLocalAddress()) { 
                    type = "site local";
                } else if (ip.isMulticastAddress()) { 
                    type = "multicast";
                } 
                    
                System.out.println("       Type      : " + type);
                System.out.println();
            }
        }         
    }
}
