package smartsockets.direct;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.StringTokenizer;

import smartsockets.util.NetworkUtils;

/**
 * This class implements a multi-SocketAddress (any number of IP addresses 
 * and port numbers).   
 *
 * It provides an immutable object used by IbisSockets for binding, connecting, 
 * or as returned values.
 *  
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 */
public class SocketAddressSet extends SocketAddress implements Comparable {
        
    private static final long serialVersionUID = -2662260670251814982L;
    
    private static final char IP_PORT_SEPERATOR = '-';
    private static final char ADDRESS_SEPERATOR = '/';
    private static final char USER_SEPERATOR = '~';
    
    private static final char EXTERNAL_START = '{';
    private static final char EXTERNAL_END = '}';
    
    private transient InetSocketAddress [] external;
    private transient InetSocketAddress [] global;
    private transient InetSocketAddress [] local;
    
    // Unfortunately, this is the least that is required for SSH-tunneling...
    private transient String user;
    
    private transient int hashCode = 0;
    private transient byte [] codedForm = null;
    private transient String toStringCache = null;
    private transient IPAddressSet addressCache;      
    private transient InetSocketAddress [] allAddressesCache;
        
    private SocketAddressSet(InetSocketAddress [] external, 
            InetSocketAddress [] global, InetSocketAddress [] local, 
            String user) {
        
        this.external = (external == null ? new InetSocketAddress[0] : external);
        this.global = (global == null ? new InetSocketAddress[0] : global);
        this.local = (local == null ? new InetSocketAddress[0] : local);        
        this.user = user;
        
        /*
        System.err.println("Created SocketAddressSet: " + toString());
        
        System.err.println("  external: " + Arrays.deepToString(this.external));
        System.err.println("  global  : " + Arrays.deepToString(this.global));
        System.err.println("  local   : " + Arrays.deepToString(this.local));
        */
        
    }
    
    /**
     * Construct a new IbisSocketAddress, starting from a byte encode version
     * 
     * @param address The InetSocketAddress.
     * @throws UnknownHostException 
     */    
    private SocketAddressSet(byte [] coded) throws UnknownHostException {
        decode(coded);
    } 

    private void decode(byte [] coded) throws UnknownHostException { 
        int index = 0;

        external = new InetSocketAddress[coded[index++] & 0xFF];
        global = new InetSocketAddress[coded[index++] & 0xFF];
        local = new InetSocketAddress[coded[index++] & 0xFF];

        int userLen = coded[index++] & 0xFF;
        
        index = decode(external, coded, index);
        index = decode(global, coded, index);
        index = decode(local, coded, index);
        
        if (userLen > 0) { 
            user = new String(coded, index, userLen);
        }        
    }    
    
    private static int decode(InetSocketAddress[] target, byte [] src, int index) 
        throws UnknownHostException { 
        
        byte [] tmp4 = null;
        byte [] tmp16 = null;
        
        for (int i=0;i<target.length;i++) { 
            
            int adlen = src[index++] & 0xFF;
            
            int port = 0;
            
            if (adlen == 4) { 
                // IPv4
                if (tmp4 == null) { 
                    tmp4 = new byte[4];
                }
                
                System.arraycopy(src, index, tmp4, 0, 4);
                index += 4;
                
                port = (src[index++] & 0xFF);
                port |= (src[index++] & 0xFF) << 8;
                
                target[i] = new InetSocketAddress(
                        InetAddress.getByAddress(tmp4), port);
            } else { 
                // IPv6
                if (tmp16 == null) { 
                    tmp16 = new byte[16];
                }
                
                System.arraycopy(src, index, tmp16, 0, 16);
                index += 16;
                
                port = (src[index++] & 0xFF);
                port |= (src[index++] & 0xFF) << 8;
                
                target[i] = new InetSocketAddress(
                        InetAddress.getByAddress(tmp16), port);
            }
            
            //address = IPAddressSet.getFromAddress(tmp);
       
        } 
    
        return index;
    }
    
        
    private static int codedSize(InetSocketAddress [] a) { 
        if (a == null || a.length == 0) { 
            return 0;
        }
        
        int len = 0;
        
        for (InetSocketAddress sa : a) { 
            if (sa.getAddress() instanceof Inet4Address) {
                len += 1 + 4 + 2;                    
            } else { 
                len += 1 + 16 + 2;
            }
        }
        
        return len;
    }
    
    private static int encode(InetSocketAddress [] a, byte [] dest, int index) { 

        if (a == null || a.length == 0) { 
            return index;
        }
        
        for (InetSocketAddress sa : a) { 
            byte [] tmp = sa.getAddress().getAddress();
            dest[index++] = (byte) (tmp.length & 0xFF);
            System.arraycopy(tmp, 0, dest, index, tmp.length);                
            index += tmp.length;

            int port = sa.getPort();
            dest[index++] = (byte) (port & 0xFF);
            dest[index++] = (byte) ((port >> 8) & 0xFF);
        }
       
        return index;
    }
    
    
    /**
     * This method returns the byte coded form of the SocketAddressSet.
     * 
     * This representation is either contains the 6 or 18 bytes of a single 
     * InetAddress + port number, or it has the form (EGL (SAP)*) where:
     *  
     *   E is the number of external addresses that follow (1 byte)
     *   G is the number of global addresses that follow (1 byte)
     *   L is the number of local addresses that follow (1 byte)
     *   S is the length of the next address (1 byte)
     *   A is an InetAddress (4 or 16 bytes)
     *   P is the port number (2 bytes)
     * 
     * @return the bytes
     */    
    public byte [] getAddress() { 
        
        if (codedForm == null) {
            
            byte [] codedUser = null;
            
            // First calculate the size of the address n bytes....
            int len = 4;
          
            len += codedSize(external);
            len += codedSize(global);
            len += codedSize(local);
            
            if (user != null && user.length() > 0) {
                codedUser = user.getBytes();
                len += codedUser.length;
            }
            
            // Now encode it...
            codedForm = new byte[len];
            
            int index = 0;
            
            codedForm[index++] = (byte) (external.length & 0xFF);
            codedForm[index++] = (byte) (global.length & 0xFF);
            codedForm[index++] = (byte) (local.length & 0xFF);
            codedForm[index++] = 
                (byte) ((codedUser == null ? 0 : codedUser.length) & 0xFF);
            
            index = encode(external, codedForm, index);
            index = encode(global, codedForm, index);
            index = encode(local, codedForm, index);
            
            if (user != null && user.length() > 0) { 
                System.arraycopy(codedUser, 0, codedForm, index, codedUser.length);
            }
        }
        
        return codedForm;
    }
    
    /**
     * Gets the InetAddressSet.
     * 
     * @return the InetAddressSet.
     */
    public IPAddressSet getAddressSet() {
        
        if (addressCache == null) {
            
            ArrayList<InetAddress> tmp = new ArrayList<InetAddress>();
            
            for (InetSocketAddress a : global) { 
                tmp.add(a.getAddress());
            }
            
            for (InetSocketAddress a : external) { 
                tmp.add(a.getAddress());
            }
        
            for (InetSocketAddress a : local) { 
                tmp.add(a.getAddress());
            }
        
            addressCache = IPAddressSet.getFromAddress(
                    tmp.toArray(new InetAddress[0]));
        }
        
        return addressCache;        
    }
    
    /**
     * Gets the SocketAddresses.
     * 
     * @return the addresses.
     */
    public InetSocketAddress [] getSocketAddresses() { 
        
        // TODO: this is a VERY BAD IDEA!!! ONLY USED IN CONNECTION SETUP!! 
        // MUST REPLACE WITH SOMETHING SMARTER ASAP!!
        
        if (allAddressesCache == null) {
            
            int len = global.length + external.length + local.length;
                
            allAddressesCache = new InetSocketAddress[len];
            
            int index = 0;
            
            System.arraycopy(global, 0, allAddressesCache, index, global.length);
            index += global.length;
            
            System.arraycopy(external, 0, allAddressesCache, index, external.length);
            index += external.length;
            
            System.arraycopy(local, 0, allAddressesCache, index, local.length);
            index += local.length;   
        }
        
        return allAddressesCache;
    } 
    

    /**
     * Gets the SSH-username
     * 
     * @return the username.
     */    
    public String getUser() { 
        return user;
    }
        
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        
        if (hashCode == 0) { 
            
            hashCode = (Arrays.hashCode(external) ^ Arrays.hashCode(global)
                ^ Arrays.hashCode(local));
            
            // Small chance, but let's fix this case anyway...
            if (hashCode == 0) { 
                hashCode = 1;
            }
        }
        
        return hashCode;
    }
                   
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object other) { 

        // Check pointers
        if (this == other) { 
            return true;
        }
        
        // Check type 
        if (!(other instanceof SocketAddressSet)) {
            return false;
        }
                
        SocketAddressSet tmp = (SocketAddressSet) other;
      
        // Finally, compare ports and addresses
        
        // NOTE: this is only correct if the addresses are exactly the same.
        // For partial addresses please use 'isCompatible'.       
        if (!compare(external, tmp.external)) { 
            return false;
        }
        
        if (!compare(global, tmp.global)) { 
            return false;
        }
        
        if (!compare(local, tmp.local)) { 
            return false;
        }
         
        return true;
    }
    
    private boolean compare(InetSocketAddress [] a, InetSocketAddress [] b) { 
     
        if (a.length != b.length) { 
            return false;
        }
       
        for (int i=0;i<a.length;i++) {
            
            InetSocketAddress ad1 = a[i];
            
            boolean gotIt = false;
            
            for (int j=0;j<b.length;j++) { 
                
                // Use an offset here, since this is more efficient when the 
                // two addresses are exactly the same. 
                InetSocketAddress ad2 = b[(j+i)%b.length];
                     
                if (ad1.equals(ad2)) {
                    gotIt = true;
                    break;
                }
            }

            if (!gotIt) { 
                return false;
            }
        }
            
        return true;    
    }
    
    private void partialToString(StringBuilder b, InetSocketAddress [] a) { 

        final int len = a.length;
        
        for (int i=0;i<len;i++) { 

            b.append(NetworkUtils.ipToString(a[i].getAddress()));

            if (i < len-1) {                
                if (a[i].getPort() != a[i+1].getPort()) { 
                    b.append(IP_PORT_SEPERATOR);
                    b.append(a[i].getPort());
                } 

                b.append(ADDRESS_SEPERATOR);
            } else { 
                b.append(IP_PORT_SEPERATOR);
                b.append(a[i].getPort());
            }
        }
    }
            
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        
        if (toStringCache == null) { 
            StringBuilder b = new StringBuilder();

            boolean needSlash = false;
            
            if (external.length > 0) { 
                b.append(EXTERNAL_START);
                partialToString(b, external);
                b.append(EXTERNAL_END);
                
                needSlash = true;
            }
            
            if (global.length > 0) {
                
                if (needSlash) { 
                    b.append(ADDRESS_SEPERATOR);
                }
                
                partialToString(b, global);
                needSlash = true;
            }
        
            if (local.length > 0) {
                
                if (needSlash) { 
                    b.append(ADDRESS_SEPERATOR);
                }
                
                partialToString(b, local);
                needSlash = true;
            }
        
            if (user != null && user.length() > 0) { 
                b.append(USER_SEPERATOR);
                b.append(user);
            }
            
            toStringCache = b.toString();
        } 
        
        return toStringCache;
    }
    
    public int compareTo(Object other) {

        if (this == other) { 
            return 0;
        }
        
        // Check type 
        if (!(other instanceof SocketAddressSet)) {
            return 0;
        }
                
        SocketAddressSet tmp = (SocketAddressSet) other;
        
        if (hashCode() < tmp.hashCode()) { 
            return -1;
        } else { 
            return 1;
        }
    }

    private static boolean compatible(InetSocketAddress [] a, 
            InetSocketAddress [] b, boolean comparePorts) { 
        
        // If either (or both) is empty we give up!
        if (a.length == 0 || b.length == 0) { 
            return false;
        }
        
        // If there is at least on shared address, we assume that they are the 
        // same. 
        for (InetSocketAddress a1 : a) { 
            for (InetSocketAddress a2 : b) { 
                
                if (comparePorts) { 
                    if (a1.getAddress().equals(a2.getAddress())) { 
                        return true;
                    }
                } else { 
                    if (a1.equals(a2)) { 
                        return true;
                    }
                }
            }
        }
           
        // Otherwise, wo do not match...
        return false;
    }
    
    private boolean isLoopBack(InetSocketAddress [] ads) { 
        if (ads == null || ads.length == 0) { 
            return false;
        }
        
        for (InetSocketAddress a : ads) { 
            if (!a.getAddress().isLoopbackAddress()) { 
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if this SocketAddressSet refers to the same machine as the 'other'
     * address. The following tests are performed:
     * 
     * if either is loopback -> return true
     * 
     * if both have global -> return (global overlap ?)
     * 
     * if both have external && !(external overlap) return false
     * 
     * if both have local -> return (local overlap)
     * 
     * else they are different machines -> return false
     * 
     * @param target
     * @return
     */
    private boolean isCompatible(SocketAddressSet other, boolean comparePorts) {
        
        // Check pointers
        if (this == other) { 
            return true;
        }

        // If either is loopback, we always match
        if (isLoopBack(local) || isLoopBack(other.local)) {
            return true;
        }
        
        // If both have 'global' addresses -MUST- overlap. 
        if (global.length > 0 && other.global.length > 0) {
            return compatible(global, other.global, comparePorts);
        } 
                
        // If both have external (NAT) addresses, they -MUST- overlap, and the 
        // local addresses -MUST- overlap also!
        if (external.length > 0 && other.external.length > 0) {
            return (compatible(external, other.external, comparePorts) && 
                compatible(local, other.local, comparePorts));   
        }
        
        // Else, just check the local addresses.
        return compatible(local, other.local, comparePorts);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        
        byte [] a = getAddress();
        
        out.writeInt(a.length);
        out.write(getAddress());
        
       // System.out.println("Writing SocketAddressSet (" + a.length + "): " + sas + " " + address);
        
    }
    
    private void readObject(ObjectInputStream in) throws IOException {
        
        int len = in.readInt();
        byte [] tmp = new byte[len];
        
     //   System.out.println("Read SocketAddressSet (" + len + "): " + sas + " " + address);
        
        in.readFully(tmp);       
        decode(tmp);   
    }
    
    public static SocketAddressSet getByAddress(byte [] coded) 
        throws UnknownHostException {
        
        return new SocketAddressSet(coded);
    }
    
    public static SocketAddressSet getByAddress(InetSocketAddress a) 
        throws UnknownHostException {
        
        if (NetworkUtils.isLocalAddress(a.getAddress())) { 
            return new SocketAddressSet(null, null, 
                    new InetSocketAddress [] { a }, null);
        } else { 
            return new SocketAddressSet(null, new InetSocketAddress [] { a }, 
                    null, null);
        }
    }
    
    /**
     * Construct a new SocketAddressSet, using IPAddressSet and a port number.
     * 
     * A valid port value is between 0 and 65535. A port number of zero will 
     * let the system pick up an ephemeral port in a bind operation.
     *
     * A null address will assign the wildcard address. 
     * 
     * @param address The IPAddressSet.
     * @param port The port number.
     */
    public static SocketAddressSet getByAddress(IPAddressSet a, int port, String user) 
        throws UnknownHostException {
        
        return getByAddress(null, null, a, new int [] { port }, user);
    }

   
    /**
     * Construct a new SocketAddressSet, using an IPAddressSet and an array 
     * of ports.
     * 
     * A valid port value is between 1 and 65535. A port number of zero is not 
     * allowed.
     *
     * A null address will assign the wildcard address. 
     * 
     * @param external The IPAddressSet containg the external addresses of the 
     *                 NAT box that this machine is behind.
     * @param externalPorts The ports of each of the external addresses. This  
     *                 should have size 1 or the same length as external has  
     *                 addresses.
     * @param other The IPAddressSet containg the addresses found on the machine 
     * @param otherPorts The ports of each of the other addresses. This  
     *                 should have size 1 or the same length as other has  
     *                 addresses.
     */
    public static SocketAddressSet getByAddress(IPAddressSet external, 
            int [] externalPorts, IPAddressSet other, int [] otherPorts, 
            String user) {
    
        if (other == null) { 
            other = IPAddressSet.getLocalHost();
        } 
    
        if (otherPorts.length != 1 && otherPorts.length != other.addresses.length) { 
            throw new IllegalArgumentException("Number of ports does not match" 
                    + "number of addresses");
        }        
    
        int numGlobal = 0;
        int numLocal = 0;
        
        for (InetAddress a : other.addresses) {     
            if (NetworkUtils.isExternalAddress(a)) { 
                numGlobal++;
            } else { 
                numLocal++;
            }
        }

        InetSocketAddress [] global = new InetSocketAddress[numGlobal];
        InetSocketAddress [] local = new InetSocketAddress[numLocal];
        
        int globalIndex = 0;
        int localIndex = 0;
        
        for (int i=0;i<other.addresses.length;i++) { 
         
            if (i < otherPorts.length && (otherPorts[i] <= 0 || otherPorts[i] > 65535)) { 
                throw new IllegalArgumentException("Port["+i+"] out of range");
            }
         
            int port = 0;
            
            if (otherPorts.length == 1) { 
                port = otherPorts[0];
            } else { 
                port = otherPorts[i];
            }
            
            if (NetworkUtils.isExternalAddress(other.addresses[i])) { 
                global[globalIndex++] = 
                    new InetSocketAddress(other.addresses[i], port);
            } else { 
                local[localIndex++] = 
                    new InetSocketAddress(other.addresses[i], port);
            }
        }
        InetSocketAddress [] extern = null;
        
        if (external == null || external.addresses.length == 0) { 
            extern = new InetSocketAddress[0];
        } else {
            
            if (externalPorts.length != 1 && externalPorts.length != external.addresses.length) { 
                throw new IllegalArgumentException("Number of external ports " +
                        "does not match number of external addresses");
            }        
        
            extern = new InetSocketAddress[external.addresses.length];
            
            for (int i=0;i<external.addresses.length;i++) { 
                
                int port = 0;
                
                if (externalPorts.length == 1) { 
                    port = externalPorts[0];
                } else { 
                    port = externalPorts[i];
                }

                extern[i] = new InetSocketAddress(external.addresses[i], port);
            }
        }
        
        return new SocketAddressSet(extern, global, local, user);
    }
    
    private static InetSocketAddress[] resize(InetSocketAddress [] orig, int add) { 
        
        InetSocketAddress [] result;
        
        if (orig == null) { 
            result = new InetSocketAddress[add];
        } else {
            result = new InetSocketAddress[orig.length + add];        
            System.arraycopy(orig, 0, result, 0, orig.length);
        } 
    
        return result;
    }
    
    private static InetSocketAddress [] addToArray(InetSocketAddress [] array, 
            LinkedList<InetAddress> ads, int port) { 
        
        int index = (array == null ? 0 : array.length);
        
        array = resize(array, ads.size());
        
        for (InetAddress a : ads) { 
            array[index++] = new InetSocketAddress(a, port);
        }
        
        ads.clear();
        
        return array;
    } 
    
    /**
     * Construct a new SocketAddresssET from a String representation of a 
     * SocketAddressSet. 
     * 
     * This representation contains any number of InetAddresses seperated by
     * ADDRESS_SEPARATOR characters (usually defined as '/'), followed by a 
     * IP_PORT_SEPERATOR (usually '-') and a port number.
     * 
     * This sequence may be repeated any number of times, separated by slashes. 
     * 
     * The following examples are valid IPv4 string representations:
     * 
     *    192.168.1.35-1234
     *    192.168.1.35/10.0.0.1-1234
     *    192.168.1.35/10.0.0.1-1234/192.31.231.65-5678
     *    192.168.1.35/10.0.0.1-1234/192.31.231.65/130.37.24.4-5678
     *    
     * We can also handle IPv6:    
     *    
     *    fe80:0:0:0:2e0:18ff:fe2c:a31%2-1234
     * 
     * Or a mix of the two: 
     * 
     *    fe80:0:0:0:2e0:18ff:fe2c:a31%2/169.254.207.84-1234 
     * 
     * External addresses (for machines behind a NAT box) are marked using curly 
     * brackets '{ }' since they are special. For example, the following address
     * identifies a NAT-ed machine with external address '82.161.4.24-5678' and 
     * internal address '192.168.1.35-1234'  
     * 
     *    {82.161.4.24-5678}/192.168.1.35-1234
     * 
     * @param addressPort The String representation of a IbisSocketAddress.
     * @throws UnknownHostException
     */
    public static SocketAddressSet getByAddress(String addressPort) 
        throws UnknownHostException { 
        
        SocketAddressSet result = parseOldStyleAddress(addressPort);
        
        if (result != null) { 
            return result;
        }
        
        return parseNewStyleAddress(addressPort);
    }
    
    private static SocketAddressSet parseOldStyleAddress(String addressPort) {
    
        int lastIndex = addressPort.lastIndexOf(':');
        
        if (lastIndex == -1) { 
            return null;
        }
        
        int port = -1;
        
        try { 
            port = Integer.parseInt(addressPort.substring(lastIndex+1));
        } catch (Exception e) {
            // It's not a valid 'old' style address....
            return null;
        }
        
        try { 
            return SocketAddressSet.getByAddress(new InetSocketAddress(
                    addressPort.substring(0, lastIndex), port));
        } catch (Exception e) {
            // It's not a valid 'old' style address....
            return null;
        }
    }
    
    private static SocketAddressSet parseNewStyleAddress(String addressPort) {
        
        StringTokenizer st = new StringTokenizer(addressPort, "{}/-~", true);
        
        boolean readingExternal = false;
        boolean readingPort = false;
        boolean readingUser = false;
                
        boolean allowExternalStart = true;
        boolean allowExternalEnd = false;
        boolean allowAddress = true;
        boolean allowSlash = false; 
        boolean allowDash = false; 
        boolean allowDone = false;
        boolean allowUser = false; 
                
        InetSocketAddress [] external = null; 
        InetSocketAddress [] global = null; 
        InetSocketAddress [] local = null; 
        
        String user = null;
        
        LinkedList<InetAddress> currentGlobal = new LinkedList<InetAddress>();
        LinkedList<InetAddress> currentLocal = new LinkedList<InetAddress>();
        
        while (st.hasMoreTokens()) { 
            
            String s = st.nextToken();
            
            System.out.println("Read: " + s);
            
            if (s.length() == 1) {
                
                char delim = s.charAt(0);
                
                switch (delim) { 
                case EXTERNAL_START: 
        
                    if (!allowExternalStart) { 
                        throw new IllegalArgumentException("Unexpected " 
                                + EXTERNAL_START + " in address(" 
                                + addressPort + ")");
                    }
                
                    allowExternalStart = false;
                    allowAddress = true;
                    allowDone = false;
                    readingExternal = true;
                    break;
                
                case EXTERNAL_END:
                    
                    if (!allowExternalEnd) {
                        throw new IllegalArgumentException("Unexpected " 
                                + EXTERNAL_END + " in address(" 
                                + addressPort + ")");
                    }
                
                    allowExternalEnd = false; 
                    allowExternalStart = true;
                    allowAddress = true;
                    readingExternal = false;
                    
                    if (local != null && local.length > 0) { 
                        allowDone = true;
                    }
                    
                    break;
                    
                case ADDRESS_SEPERATOR: 
                    
                    if (!allowSlash) {
                        throw new IllegalArgumentException("Unexpected " 
                                + ADDRESS_SEPERATOR + " in address(" 
                                + addressPort + ")");
                    }
                    
                    allowSlash = false;
                    allowAddress = true;
                    break;
                    
                case IP_PORT_SEPERATOR: 
                    
                    if (!allowDash) { 
                        throw new IllegalArgumentException("Unexpected " 
                                + IP_PORT_SEPERATOR + " in address(" 
                                + addressPort + ")");
                    }
                    
                    allowDash = false;
                    readingPort = true;
                    break;
        
                case USER_SEPERATOR: 
                    
                    if (!allowUser) { 
                        throw new IllegalArgumentException("Unexpected " 
                                + USER_SEPERATOR + " in address(" 
                                + addressPort + ")");
                    }

                    allowDone = false;
                    allowUser = false;
                    readingUser = true;
                    break;
                    
                default:
                    // should never happen ? 
                    throw new IllegalArgumentException("Unexpected delimiter: " 
                            + delim + " in address(" + addressPort + ")");
                }
                
            } else if (readingUser) { 
                
                user = s;
                readingUser = false;
                allowSlash = false;
                allowDone = true;
                
            } else if (readingPort) { 
                
                // This should complete a group of addresses. More may folow
                int port = Integer.parseInt(s);
                
                // ... do a sanity check on the port value ...
                if (port <= 0 || port > 65535) { 
                    throw new IllegalArgumentException("Port out of range: " + port);
                }
                    
                if (readingExternal) { 
                    external = addToArray(external, currentGlobal, port);
                } else { 
                    global = addToArray(global, currentGlobal, port);
                    local = addToArray(local, currentLocal, port);
                }
                
                readingPort = false;
                allowSlash = true;
                
                if (readingExternal) { 
                    allowExternalEnd = true;
                } else { 
                    allowDone = true;
                    allowUser = true;
                }
                 
            } else if (allowAddress) { 
                
                // reading address
                InetAddress tmp = null;
                
                try { 
                    tmp = InetAddress.getByName(s);
                } catch (UnknownHostException e) { 
                    throw new IllegalArgumentException("Broken inet address " 
                            + s + " in address(" + addressPort + ")");
                }
                
                if (NetworkUtils.isLocalAddress(tmp)) { 
                    currentLocal.add(tmp);
                } else { 
                    currentGlobal.add(tmp);
                }
                
                allowSlash = true;
                allowDash = true;
                allowAddress = false;
                allowDone = false;
            } else { 
                throw new IllegalArgumentException("Unexpected data " 
                        + s + " in address(" + addressPort + ")");
            }
        }
        
        if (!allowDone) { 
            throw new IllegalArgumentException("Address " + addressPort 
                    + " is incomplete!");
        }
        
        return new SocketAddressSet(external, global, local, user);
    }
    
    public static SocketAddressSet getByAddress(String host, int port) throws UnknownHostException { 
        return getByAddress(IPAddressSet.getFromString(host), port, null);
    }
    
    
    private static InetSocketAddress [] merge(InetSocketAddress [] a, 
            InetSocketAddress [] b) { 
        
        int alen = (a == null ? 0 : a.length);
        int blen = (b == null ? 0 : b.length);
        
        InetSocketAddress [] res = new InetSocketAddress[alen + blen];
        
        if (alen > 0) { 
            System.arraycopy(a, 0, res, 0, alen);
        } 
        
        if (blen > 0) { 
            System.arraycopy(b, 0, res, alen, blen);
        } 
        
        return res;
    }
    
    /**
     * Merges two IbisSocketAddresses. 
     * 
     * @param s1 the first IbisSocketAddress
     * @param s2 the second IbisSocketAddress
     * @return a new SmartSocketAddress
     */
    public static SocketAddressSet merge(SocketAddressSet s1, 
            SocketAddressSet s2) {
        
        String user = s1.user;
        
        if (s1.user == null || user.length() == 0) {
            user = s2.user;           
        } else if (s2.user != null && s2.user.length() > 0) {
               
            if (!s1.user.equals(s2.user)) { 
                throw new IllegalArgumentException("Cannot merge two " +
                "addresses with different user names!");
            } 
                
            // They are equal...
            user = s2.user;  
        } else { 
            user = s2.user;
        }
        
        return new SocketAddressSet(
                merge(s1.external, s2.external), 
                merge(s1.global, s2.global), 
                merge(s1.local, s2.local), user);
    } 
    
    /**
     * Converts an array of SocketAddressSets to a String array.
     * 
     * @param s the array of {@link SocketAddressSet}
     * @return a new String array containing the {@link String} representations 
     * of the {@link SocketAddressSet}s, or <code>null</code> if 
     * s was <code>null</code> 
     */
    public static String [] convertToStrings(SocketAddressSet [] s) {

        if (s == null) { 
            return null;
        }
        
        String [] result = new String[s.length];
        
        for (int i=0;i<s.length;i++) {            
            if (s[i] != null) {             
                result[i] = s[i].toString();
            }
        }
        
        return result;
    } 

    /**
     * Converts an array of Strings into an array of SocketAddressSets.  
     * 
     * @param s the array of {@link String} to convert
     * @param ignoreProblems indicates if conversion problems should be silently 
     * ignored. 
     * @return a new array containing the {@link SocketAddressSet}s 
     * or <code>null</code> if s was <code>null</code> 
     * @throws UnknownHostException when any of the Strings cannot be converted 
     * and ignoreProblems is false 
     */
    public static SocketAddressSet [] convertToSocketAddressSet(String [] s, 
            boolean ignoreProblems) throws UnknownHostException {

        if (s == null) { 
            return null;
        }
        
        SocketAddressSet [] result = new SocketAddressSet[s.length];
        
        for (int i=0;i<s.length;i++) {            
            if (s[i] != null) { 
                if (ignoreProblems) {                 
                    try {                   
                        result[i] = SocketAddressSet.getByAddress(s[i]);
                    } catch (UnknownHostException e) {
                        // ignore
                    }
                } else { 
                    result[i] = SocketAddressSet.getByAddress(s[i]);
                } 
            }
        }
        
        return result;
    } 

    /**
     * Converts an array of Strings into an array of SocketAddressSets.  
     * 
     * @param s the array of {@link String} to convert
     * @return a new array containing the {@link SocketAddressSet}s 
     * or <code>null</code> if s was <code>null</code> 
     * @throws UnknownHostException when any of the Strings cannot be converted 
     */
    public static SocketAddressSet [] convertToSocketAddressSet(String [] s) 
        throws UnknownHostException {
        return convertToSocketAddressSet(s, false);
    } 

    /**
     * Returns if the other SocketAddressSet represents the same machine as 
     * this one.   
     * 
     * @param other the SocketAddressSet to compare to
     * @return if both SocketAddressSets represent the same machine
     */
    public boolean sameMachine(SocketAddressSet other) { 
        return isCompatible(other, false);
    }

    /**
     * Returns if the other SocketAddressSet represents the same process as 
     * this one.   
     * 
     * @param other the SocketAddressSet to compare to
     * @return if both SocketAddressSets represent the same process
     */
    public boolean sameProcess(SocketAddressSet other) {
        return isCompatible(other, true);
    }
    
    /**
     * Returns if the two SocketAddressSets represent the same machine. 
     * 
     */
    public static boolean sameMachine(SocketAddressSet a, SocketAddressSet b) { 
        return a.sameProcess(b);
    }

    /**
     * Returns if the two SocketAddressSets represent the same process. 
     * 
     */
    public static boolean sameProcess(SocketAddressSet a, SocketAddressSet b) {
        return a.sameProcess(b);
    }

    public int numberOfAddresses() {
        return external.length + global.length + local.length;
    }

  
}
