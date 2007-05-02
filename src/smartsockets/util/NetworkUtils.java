package smartsockets.util;


import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import smartsockets.util.net.NativeNetworkConfig;

/**
 * This class contains some network related utilities to deal with IP addresses.
 */
public class NetworkUtils {

    protected static Logger logger;
    
    static {
        ibis.util.Log.initLog4J("smartsockets");
        logger = Logger.getLogger("smartsockets.network.util");
    }
    
    // The number of milliseconds between October 15th 1582 (the start of the 
    // Gregorian calendar) and January 1st 1970 (the start of Unix time). This
    // is needed in UUID generation.     
    private static long MILLIS_1582_1970 = 12216618000000L;
    
    // This matrix contains the possible IPv4 subnet/mask values. Note that the 
    // each entry consists of three parts: the subnet, the mask needed to match 
    // the address, and the real mask that is returned. This allows a range of 
    // local addresses (e.g., 192.168.0.xxx to 192.168.255.xxx) to be matched in 
    // one go.
    private static byte [][] localSubnetMask = new byte[][] { 
        
         // host local, 127/8
        { 127,0,0,0 }, 
        { (byte) 0xff,0,0,0 },  
        { (byte) 0xff,0,0,0 },  
                
        // site local, 10/8
        { 10,0,0,0 },  
        { (byte) 0xff,0,0,0 },
        { (byte) 0xff,0,0,0 },       
         
        // site local, 172.16/12 to 172.31/12
        { (byte) (172 & 0xff), (byte) (16 & 0xff), 0, 0 }, 
        { (byte) 0xff, (byte) 0xf0, 0, 0 },  // <-- YES, it's really 0xf0!!   
        { (byte) 0xff, (byte) 0xff, 0, 0 },
        
        // site local, 192.168.0/24 to 192.168.255/24 
        { (byte) (192 & 0xff), (byte) (168 & 0xff), 0, 0 }, 
        { (byte) 0xff, (byte) 0xff, 0, 0 },
        { (byte) 0xff, (byte) 0xff, (byte) 0xff, 0 },
                
        // link local, 169.254/16
        { (byte) (169 & 0xff), (byte) (254 & 0xff), 0, 0 }, 
        { (byte) 0xff, (byte) 0xff, 0, 0 },
        { (byte) 0xff, (byte) 0xff, 0, 0 }
        
        // TODO: add ipv6 ???
    };

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
     * Returns true if the array contains an address equal to the given address. 
     * 
     * @param inas the array of addresses
     * @param a the address to check 
     * @return <code>true</code> if an address equal to a is part of the array, 
     * <code>false</code> otherwise.
     */
    public static boolean contains(InetSocketAddress [] inas, 
            InetSocketAddress a) {
        
        if (inas == null || inas.length == 0 || a == null) { 
            return false;
        }
        
        for (InetSocketAddress sa : inas) {
            if (sa != null && a.equals(sa)) { 
                return true;
            }
        }

        return false;        
    }
    
    /**
     * Adds all the network interfaces found on this machine to the list.
     */    
    private static ArrayList<NetworkInterface> getNetworkInterfacesList() { 
        
        ArrayList<NetworkInterface> list = new ArrayList<NetworkInterface>();        
        Enumeration<NetworkInterface> e = null;

        try {
            e = NetworkInterface.getNetworkInterfaces();                       

            while (e.hasMoreElements()) {                   
                list.add(e.nextElement());
            }        
        } catch (SocketException ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not get network interfaces.");
            }
        }
        
        return list;
    }
    
    /**
     * Returns all the network interfaces found on this machine.
     * 
     * @return the network interfaces.
     */
    public static NetworkInterface [] getNetworkInterfaces() { 
        ArrayList<NetworkInterface> list = getNetworkInterfacesList();       
        
        return list.toArray(new NetworkInterface[list.size()]);
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
    private static void getAllHostAddresses(NetworkInterface nw, 
            List<InetAddress> target, boolean ignoreLoopback, boolean ignoreIP6) { 
                
        if (logger.isInfoEnabled()) {
            logger.info("   " + nw.getDisplayName() + ":");
        }
                        
        Enumeration<InetAddress> e2 = nw.getInetAddresses();
            
        while (e2.hasMoreElements()) {
            
            InetAddress tmp = e2.nextElement();
                       
            boolean t1 = !ignoreLoopback || !tmp.isLoopbackAddress();
            boolean t2 = !ignoreIP6 || (tmp instanceof Inet4Address); 
            
            if (t1 && t2) {
                if (logger.isInfoEnabled()) {
                    logger.info("    - " + tmp.getHostAddress() + " (used)");
                }
                target.add(tmp);
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("    - " + tmp.getHostAddress() + " (ignored)");
                }
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

        ArrayList<NetworkInterface> nwl = getNetworkInterfacesList();
        ArrayList<InetAddress> list = new ArrayList<InetAddress>();
            
        if (logger.isInfoEnabled()) {
            logger.info("Determining available addresses:");
        }
        
        for (NetworkInterface nwi : nwl) { 
            getAllHostAddresses(nwi, list, ignoreLoopback, ignoreIP6);
        } 
        
        return list.toArray(new InetAddress[list.size()]);
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
        ArrayList<InetAddress> list = new ArrayList<InetAddress>();
        getAllHostAddresses(nw, list, ignoreLoopback, ignoreIP6);
        return list.toArray(new InetAddress[list.size()]);
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
     
        StringBuilder tmp = new StringBuilder(); 
        
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
     * Converts a String to an array of bytes. The values are sepated by a 
     * user-specified seperator. If required, the bytes are  read from a 
     * hexadecimal form.  
     *
     * @param s the String to convert
     * @param hex specifies if a hexadecimal input is used.     
     * @param sep the seperator used
     *  
     * @return the byte array representation of the address.
     */        
    private static byte [] stringToBytes(String s, boolean hex, char sep) { 
     
        StringTokenizer st = new StringTokenizer(s, "" + sep);
        
        int len = st.countTokens();
        
        byte [] result = new byte[len];
        
        for (int i=0;i<len;i++) { 

            String tmp = st.nextToken();
            
            if (hex) { 
                result[i] = (byte) (0xff & Integer.parseInt(tmp, 16));
            } else { 
                result[i] = (byte) (0xff & Integer.parseInt(tmp, 10));
            }
        }
        
        return result;
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
     * Converts an array of bytes containing a UUID to a String. 
     * A 16 byte UUID is expected as input. A dot will be used as a 
     * seperator.    
     * 
     * @param UUID the UUID to convert
     * @return the String representation of the UUID.
     */            
    public static String UUIDToString(byte [] UUID) { 
        return bytesToString(UUID, true, '.');        
    }
    
    /**
     * Converts a String representation of a UUID to an array of bytes. 
     * A 32 hex character, dot seperated UUID is expected as input.  
     * 
     * @param UUID the UUID to convert
     * @return the byte array representation of the UUID.
     */            
    
    public static byte [] StringToUUID(String UUID) { 
        return stringToBytes(UUID, true, '.');
    }
    
    public static byte [] getUUID() { 
        
        // Get the time since the start of the calendar
        long time = (System.currentTimeMillis() + MILLIS_1582_1970) * 10;
        
        // Or in the version number
        time |= 2;

        // Get a random 'clock' value
        Random r = new Random();
        int clock = r.nextInt((2 << 16) - 1);

        // Or in the variant code
        clock |= (1 << 2);
        
        // Get the MAC address
        byte [] mac = NetworkUtils.getAnyMACAddress(
                getAllHostAddresses(false, true));        

        if (mac == null) { 
            // If we failed to get a MAC address, we use a random value instead.
            mac = new byte[6];
            r.nextBytes(mac);
        }
        
        // Finally construct the UUID
        byte [] uuid = new byte[16];
        
        for (int i=7;i>=0;i--) { 
            uuid[i] = (byte) ((time >> i*8) & 0xff); 
        }
        
        for (int i=1;i>=0;i--) { 
            uuid[8+i] = (byte) ((clock >> i*8) & 0xff); 
        }
        
        for (int i=0;i<6;i++) { 
            uuid[10+i] = mac[i]; 
        }
        
        return uuid;
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
    
    /**
     * Checks if the given address (in byte representation) matches a certain 
     * subnet/netmask. 
     * 
     * @param ad byte representation of the adress to check
     * @param sub the array containing the subnet
     * @param mask the array containing the netmask
     * 
     * @return if the address matches the subnet/netmask
     */    
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
   
    /**
     * Checks if the given address matches a certain subnet/netmask 
     * 
     * @param ad the address to check
     * @param sub the array containing the subnet
     * @param mask the array containing the netmask
     * 
     * @return if the address matches the subnet/netmask
     */            
    public static boolean matchAddress(InetAddress ad, byte [] sub, 
            byte [] mask) {         
        return matchAddress(ad.getAddress(), sub, mask);
    }

    /**
     * Produces a sensible subnet/netmask for a give -local- address. 
     * 
     * @param ad the address for which to produce the subnet/netmask
     * @param sub the array containing the subnet
     * @param mask the array containing the netmask
     * 
     * @throws IllegalArgumentException if the given address isn't local or if 
     * the given byte arrays have a different length than the subnet/netmask. 
     *
     */            
    public static boolean getSubnetMask(byte [] a, byte [] sub, 
            byte [] mask) {        
        
        if (sub.length != a.length) {  
            throw new IllegalArgumentException("Subnet array has wrong size!");
        }
        
        if (mask.length != a.length) {        
            throw new IllegalArgumentException("Mask array has wrong size!");
        }
        
        for (int i=0;i<localSubnetMask.length;i+=3) { 
            
            if (matchAddress(a, localSubnetMask[i], localSubnetMask[i+1])) { 
                // Selectively copy the address....
                for (int b=0;b<sub.length;b++) {
                    mask[b] = localSubnetMask[i+2][b];
                    sub[b] = (byte) (a[b] & mask[b]);                    
                } 
                return true;
            } 
        }
        
        // oh dear,we didn't find the address (TODO: it may be IPv6). Just
        // return false for now...
        return false;
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

    public static byte [] getAnyMACAddress(InetAddress [] ips) {
        
        byte [] mac = null;
        
        for (InetAddress ip : ips) {
            try { 
                mac = NativeNetworkConfig.getMACAddress(ip);

                if (mac != null) { 
                    return mac;
                }
            } catch (IOException e) {
                // ignore and try the next one....
            }
        }
        
        return null;
    }

    
    public static byte [] getNetmask(InetAddress ip) throws IOException { 
        return NativeNetworkConfig.getNetmask(ip);
    }
    
    public static byte [] getBroadcast(InetAddress ip) throws IOException { 
        return NativeNetworkConfig.getBroadcast(ip);
    }
    
    
    public static void main(String [] args) { 
        
        ArrayList<NetworkInterface> nwl = getNetworkInterfacesList();
        
        for (NetworkInterface nwi : nwl) { 
            
            System.out.println("Found network interface: " 
                    + nwi.getDisplayName()); 
            
            ArrayList<InetAddress> ips = new ArrayList<InetAddress>(); 
            
            getAllHostAddresses(nwi, ips, false, true);
            
            String mac = null;
            
            for (InetAddress ip : ips) { 
                try { 
                    mac = getMACAddressAsString(ip);
                } catch (Exception e) {
                    // ignore
                }
                
                if (mac != null) { 
                    break;
                }
            }

            System.out.println("  MAC Address    : " + (mac != null ? mac : "unknown"));  
                       
            for (InetAddress ip : ips) { 
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
