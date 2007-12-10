package ibis.smartsockets.direct;


import ibis.smartsockets.util.NetworkUtils;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
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


/**
 * This class implements a multi SocketAddress (any number of IP addresses 
 * and port numbers).   
 *
 * It provides an immutable object used by SmartSockets for binding, connecting, 
 * or as returned values.
 *  
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 */
public class DirectSocketAddress extends SocketAddress implements Comparable {
        
    private static final long serialVersionUID = -2662260670251814982L;
    
    private static final char IP_PORT_SEPERATOR = '-';
    private static final char ADDRESS_SEPERATOR = '/';
    private static final char USER_SEPERATOR = '~';
    private static final char UUID_SEPERATOR = '#';
        
    private static final char EXTERNAL_START = '{';
    private static final char EXTERNAL_END = '}';
   
    // Should contain all of the above!
    private static final String SEPARATORS = "{}-/~#";
    
    private transient InetSocketAddress [] externalAds;
    private transient InetSocketAddress [] publicAds;
    private transient InetSocketAddress [] privateAds;
    private transient byte [] UUID;
        
    // Unfortunately, this is the least that is required for SSH-tunneling...
    private transient String user;
    
    private transient int hashCode = 0;
    private transient byte [] codedForm = null;
    private transient String toStringCache = null;
    private transient IPAddressSet addressCache;      
    private transient InetSocketAddress [] allAddressesCache;
    
    private DirectSocketAddress(InetSocketAddress [] externalAds, 
            InetSocketAddress [] publicAds, InetSocketAddress [] privateAds, 
            byte [] UUID, String user) {
        
        this.externalAds = (externalAds == null ? new InetSocketAddress[0]:externalAds);
        this.publicAds = (publicAds == null ? new InetSocketAddress[0] : publicAds);
        this.privateAds = (privateAds == null ? new InetSocketAddress[0] : privateAds);
        this.UUID = UUID;
        this.user = user;        
    }
    
    /**
     * Construct a new IbisSocketAddress, starting from a byte encode version
     * 
     * @param address The InetSocketAddress.
     * @throws UnknownHostException 
     */    
    private DirectSocketAddress(byte [] coded, int off) 
        throws UnknownHostException {
        
        decode(coded, off);
    } 

    private void decode(byte [] coded, int off) throws UnknownHostException { 
        int index = off;

        externalAds = new InetSocketAddress[coded[index++] & 0xFF];
        publicAds = new InetSocketAddress[coded[index++] & 0xFF];
        privateAds = new InetSocketAddress[coded[index++] & 0xFF];
        
        int uuidLen = coded[index++];
        int userLen = coded[index++] & 0xFF;
        
        index = decode(externalAds, coded, index);
        index = decode(publicAds, coded, index);
        index = decode(privateAds, coded, index);
        
        if (uuidLen > 0) {
            UUID = new byte[uuidLen];
            System.arraycopy(coded, index, UUID, 0, uuidLen);
            index += uuidLen;
        }        
        
        if (userLen > 0) { 
            user = new String(coded, index, userLen);
        }        
    }    
    
    private static int decode(InetSocketAddress[] target, byte [] src, 
            int index) throws UnknownHostException { 
        
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
     * InetAddress + port number, or it has the form (EGLMN (SAP)* (U)*) where:
     *  
     *   E is the number of external addresses that follow (1 byte)
     *   G is the number of public addresses that follow (1 byte)
     *   L is the number of private addresses that follow (1 byte)
     *   M is the length of the UUID (1 byte, normally 0 or 16)
     *   N is the length of the username (1 byte, 0 if unused)
     *   S is the length of the next address (1 byte)
     *   A is an InetAddress (4 or 16 bytes)
     *   P is the port number (2 bytes)
     *   U is a UUID (16 bytes)
     * 
     * @return the bytes
     */    
    public byte [] getAddress() { 
        
        if (codedForm == null) {
            
            byte [] codedUser = null;
            
            // First calculate the size of the address n bytes....
            int len = 5;
          
            len += codedSize(externalAds);
            len += codedSize(publicAds);
            len += codedSize(privateAds);
            
            if (UUID != null) { 
                len += UUID.length;
            }
            
            if (user != null && user.length() > 0) {
                codedUser = user.getBytes();
                len += codedUser.length;
            }
            
            // Now encode it...
            codedForm = new byte[len];
            
            int index = 0;
            
            codedForm[index++] = (byte) (externalAds.length & 0xFF);
            codedForm[index++] = (byte) (publicAds.length & 0xFF);
            codedForm[index++] = (byte) (privateAds.length & 0xFF);
            codedForm[index++] = (byte) ((UUID == null ? 0 : UUID.length) & 0xFF);
            codedForm[index++] = 
                (byte) ((codedUser == null ? 0 : codedUser.length) & 0xFF);
            
            index = encode(externalAds, codedForm, index);
            index = encode(publicAds, codedForm, index);
            index = encode(privateAds, codedForm, index);
            
            if (UUID != null) { 
                System.arraycopy(UUID, 0, codedForm, index, UUID.length);
                index += UUID.length; 
            }
            
            if (user != null && user.length() > 0) { 
                System.arraycopy(codedUser, 0, codedForm, index, codedUser.length);
            }
        }
        
        return codedForm.clone();
    }
    
    /**
     * Gets the InetAddressSet.
     * 
     * @return the InetAddressSet.
     */
    public IPAddressSet getAddressSet() {
        
        if (addressCache == null) {
            
            ArrayList<InetAddress> tmp = new ArrayList<InetAddress>();
            
            for (InetSocketAddress a : publicAds) { 
                tmp.add(a.getAddress());
            }
            
            for (InetSocketAddress a : externalAds) { 
                tmp.add(a.getAddress());
            }
        
            for (InetSocketAddress a : privateAds) { 
                tmp.add(a.getAddress());
            }
        
            addressCache = IPAddressSet.getFromAddress(
                    tmp.toArray(new InetAddress[0]));
        }
        
        return addressCache;        
    }
    
    /**
     * Return the port numbers used in this DirectSocketAddress. 
     * 
     * @param includeExternal should external (NAT) addresses be included ?
     * @return the port numbers used in this DirectSocketAddress.
     */
    public int [] getPorts(boolean includeExternal) { 
        
        int len = publicAds.length + privateAds.length;
        
        if (includeExternal) { 
            len += externalAds.length;
        }
        
        int [] result = new int[len];
        
        int index = 0;
        
        for (InetSocketAddress i : publicAds) { 
            result[index++] = i.getPort();
        }
        
        for (InetSocketAddress i : privateAds) { 
            result[index++] = i.getPort();
        }
        
        if (includeExternal) { 
            for (InetSocketAddress i : externalAds) { 
                result[index++] = i.getPort();
            }
        }
        
        return result;
    }
    
    /**
     * Gets the SocketAddresses.
     * 
     * @return the addresses.
     */
    protected InetSocketAddress [] getSocketAddresses() { 
        
        // TODO: this is a VERY BAD IDEA!!! ONLY USED IN CONNECTION SETUP!! 
        // MUST REPLACE WITH SOMETHING SMARTER ASAP!!
        
        if (allAddressesCache == null) {
            
            int len = publicAds.length + externalAds.length + privateAds.length;
                
            allAddressesCache = new InetSocketAddress[len];
            
            int index = 0;
            
            System.arraycopy(publicAds, 0, allAddressesCache, index, publicAds.length);
            index += publicAds.length;
            
            System.arraycopy(externalAds, 0, allAddressesCache, index, externalAds.length);
            index += externalAds.length;
            
            System.arraycopy(privateAds, 0, allAddressesCache, index, privateAds.length);
            // index += privateAds.length;   
        }
        
        return allAddressesCache;
    } 
    
    /**
     * Return the external addresses of this DirectSocketAddress. 
     * 
     * @return an array containing the external addresses of this 
     *         DirectSocketAddress
     */
    public InetSocketAddress [] getExternalAddresses() {
        return externalAds.clone();
    }
    
    /**
     * Return the public addresses of this DirectSocketAddress. 
     * 
     * @return an array containing the public addresses of this 
     *         DirectSocketAddress
     */
    public InetSocketAddress [] getPublicAddresses() {
        return publicAds.clone();
    }
    
    /**
     * Return the private addresses of this DirectSocketAddress. 
     * 
     * @return an array containing the private addresses of this 
     *         DirectSocketAddress
     */
    public InetSocketAddress [] getPrivateAddresses() {
        return privateAds.clone();
    }
    
    /**
     * Returns is this DirectSocketAddress has any public addresses. 
     * 
     * @return <code>true</code> if this DirectSocketAddress has any public 
     * addresses, <code>false</code> otherwise.
     */
    public boolean hasPublicAddress() {
        return publicAds != null && publicAds.length > 0;
    }    
    
    /**
     * Returns if the given InetSocketAddress is one of the external addresses 
     * of this DirectSocketAddress. 
     * 
     * @return <code>true</code> if the given InetSocketAddress is one of the 
     * external addresses, <code>false</code> otherwise.
     */
    public boolean inExternalAddress(InetSocketAddress a) {
        return NetworkUtils.contains(externalAds, a);
    }
    
    /**
     * Returns if the given InetSocketAddress is one of the public addresses 
     * of this DirectSocketAddress. 
     * 
     * @return <code>true</code> if the given InetSocketAddress is one of the 
     * public addresses, <code>false</code> otherwise.
     */
    public boolean inPublicAddress(InetSocketAddress a) {
        return NetworkUtils.contains(publicAds, a);
    }    
    
    /**
     * Returns if the given InetSocketAddress is one of the private addresses 
     * of this DirectSocketAddress. 
     * 
     * @return <code>true</code> if the given InetSocketAddress is one of the 
     * private addresses, <code>false</code> otherwise.
     */
    public boolean inPrivateAddress(InetSocketAddress a) {
        return NetworkUtils.contains(privateAds, a);
    }
    
    /**
     * Returns the username of the DirectSocketAddress. 
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
            
            hashCode = (Arrays.hashCode(externalAds) 
                    ^ Arrays.hashCode(publicAds)
                    ^ Arrays.hashCode(privateAds));
            
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

        // NOTE: this checks if the addresses are exactly the same.
        // For partial comparisons please use 'isCompatible'.       

        // Check pointers
        if (this == other) { 
            return true;
        }
        
        // Check type 
        if (!(other instanceof DirectSocketAddress)) {
            return false;
        }
                
        DirectSocketAddress tmp = (DirectSocketAddress) other;
      
        // First, compare UUIDs 
        if (!Arrays.equals(UUID, tmp.UUID)) { 
            return false;
        } 
                 
        // Next, compare ports and addresses..        
        if (!compare(externalAds, tmp.externalAds)) { 
            return false;
        }
        
        if (!compare(publicAds, tmp.publicAds)) { 
            return false;
        }
        
        if (!compare(privateAds, tmp.privateAds)) { 
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
            
            if (externalAds.length > 0) { 
                b.append(EXTERNAL_START);
                partialToString(b, externalAds);
                b.append(EXTERNAL_END);
                
                needSlash = true;
            }
            
            if (publicAds.length > 0) {
                
                if (needSlash) { 
                    b.append(ADDRESS_SEPERATOR);
                }
                
                partialToString(b, publicAds);
                needSlash = true;
            }
        
            if (privateAds.length > 0) {
                
                if (needSlash) { 
                    b.append(ADDRESS_SEPERATOR);
                }
                
                partialToString(b, privateAds);
                needSlash = true;
            }
        
            if (UUID != null) { 
                b.append(UUID_SEPERATOR);
                b.append(NetworkUtils.UUIDToString(UUID));
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
        if (!(other instanceof DirectSocketAddress)) {
            return 0;
        }
                
        DirectSocketAddress tmp = (DirectSocketAddress) other;
        
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
                
                if (!comparePorts) { 
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
     * if both have public -> return (public overlap ?)
     * 
     * if both have external && !(external overlap) return false
     * 
     * if both have private -> return (private overlap)
     * 
     * else they are different machines -> return false
     * 
     * @param target
     * @return
     */
    private boolean isCompatible(DirectSocketAddress other, boolean comparePorts) {
        
        // Check pointers
        if (this == other) { 
            return true;
        }
        
        // Check UUIDs. Three cases fail here, either both have a UUID and they
        // are different, or either has a UUID and the other has one or more
        // public addresses. All other combination pass on to the next tests... 
        if (UUID != null) {            
            if (other.UUID != null) { 
                if (!Arrays.equals(UUID, other.UUID)) {
                    return false;
                } 
            } else { 
                if (other.publicAds.length > 0) { 
                    // I have a UUID so I must only have private addresses, 
                    // while the other has public addresses as well...  
                    return false;
                }
            }
        } else { 
            if (other.UUID != null && publicAds.length > 0) { 
                // The other has a UUID so it must only have private addresses, 
                // while I have public addresses as well...  
                return false;
            }
        }
        
        // If either is loopback, we always match
        if (isLoopBack(privateAds) || isLoopBack(other.privateAds)) {
            return true;
        }
        
        // If both have 'public' addresses, they -MUST- overlap. 
        if (publicAds.length > 0 && other.publicAds.length > 0) {
            return compatible(publicAds, other.publicAds, comparePorts);
        } 
                
        // If both have external (NAT) addresses, they -MUST- overlap, and the 
        // private addresses -MUST- overlap also!
       
        // TODO: does this work in the multiple NAT case ? We should also have a
        //   UUID there.... 
        if (externalAds.length > 0 && other.externalAds.length > 0) {
            return (compatible(externalAds, other.externalAds, comparePorts) && 
                compatible(privateAds, other.privateAds, comparePorts));   
        }
        
        // Else, just check the private addresses.
        return compatible(privateAds, other.privateAds, comparePorts);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        write(this, out);
    }
    
    private void readObject(ObjectInputStream in) throws IOException {
        int len = in.readInt();
        byte [] tmp = new byte[len];
        
        in.readFully(tmp);       
        decode(tmp, 0);
    }
       
    public static void write(DirectSocketAddress s, DataOutput out) throws IOException { 
        
        if (s == null) {
            out.writeInt(0);
        } else {            
            byte [] a = s.getAddress();
        
            out.writeInt(a.length);
            out.write(a);
        }
    }
   
    public static DirectSocketAddress read(DataInput in) throws IOException { 
        
        int len = in.readInt();
        
        if (len == 0) { 
            return null;
        }
        
        byte [] tmp = new byte[len];
        
        in.readFully(tmp);       
      
        return new DirectSocketAddress(tmp, 0);   
    }
   
    public static void skip(DataInputStream in) throws IOException { 
        
        int len = in.readInt();
        
        if (len == 0) { 
            return;
        }
       
        while (len > 0) {
            len -= in.skip(len);
        }
    }
   
    public static DirectSocketAddress fromBytes(byte [] coded) 
        throws UnknownHostException {
        return fromBytes(coded, 0);
    }

    
    public static DirectSocketAddress fromBytes(byte [] coded, int off) 
        throws UnknownHostException {
        
        return new DirectSocketAddress(coded, off);
    }
  
    public static DirectSocketAddress getByAddress(InetSocketAddress a) 
        throws UnknownHostException {
        
        if (NetworkUtils.isLocalAddress(a.getAddress())) { 
            return new DirectSocketAddress(null, null, 
                    new InetSocketAddress [] { a }, null, null);
        } else { 
            return new DirectSocketAddress(null, new InetSocketAddress [] { a }, 
                    null, null, null);
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
    public static DirectSocketAddress getByAddress(IPAddressSet a, int port, String user) 
        throws UnknownHostException {
        
        return getByAddress(null, null, a, new int [] { port }, user);
    }

    public static DirectSocketAddress getByAddress(IPAddressSet a, int port) 
        throws UnknownHostException {    
        return getByAddress(null, null, a, new int [] { port }, null);
    }
    
    public static DirectSocketAddress getByAddress(IPAddressSet external, 
            int externalPort, IPAddressSet other, int otherPort,  
            String user) {
        
        return getByAddress(external, new int [] { externalPort }, other, 
                new int [] { otherPort }, user);
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
    public static DirectSocketAddress getByAddress(IPAddressSet external, 
            int [] externalPorts, IPAddressSet other, int [] otherPorts, 
            String user) {
    
        if (other == null) { 
            other = IPAddressSet.getLocalHost();
        } 
    
        if (otherPorts.length != 1 && otherPorts.length != other.addresses.length) { 
            throw new IllegalArgumentException("Number of ports does not match" 
                    + "number of addresses");
        }        
    
        int numPublic = 0;
        int numPrivate = 0;
        
        for (InetAddress a : other.addresses) {     
            if (NetworkUtils.isExternalAddress(a)) { 
                numPublic++;
            } else { 
                numPrivate++;
            }
        }

        InetSocketAddress [] publicAds = new InetSocketAddress[numPublic];
        InetSocketAddress [] privateAds = new InetSocketAddress[numPrivate];
        
        int publicIndex = 0;
        int privateIndex = 0;
        
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
                publicAds[publicIndex++] = 
                    new InetSocketAddress(other.addresses[i], port);
            } else { 
                privateAds[privateIndex++] = 
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
        
        return new DirectSocketAddress(extern, publicAds, privateAds, other.UUID, user);
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
    public static DirectSocketAddress getByAddress(String addressPort) 
        throws UnknownHostException { 
        
        DirectSocketAddress result = parseOldStyleAddress(addressPort);
        
        if (result != null) { 
            return result;
        }
        
        return parseNewStyleAddress(addressPort);
    }
    
    private static DirectSocketAddress parseOldStyleAddress(String addressPort) {
    
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
            return DirectSocketAddress.getByAddress(new InetSocketAddress(
                    addressPort.substring(0, lastIndex), port));
        } catch (Exception e) {
            // It's not a valid 'old' style address....
            return null;
        }
    }

    private static DirectSocketAddress parseNewStyleAddress(String addressPort) {
        
        StringTokenizer st = new StringTokenizer(addressPort, SEPARATORS, true);
        
        boolean readingExternal = false;
        boolean readingPort = false;
        boolean readingUUID = false;        
        boolean readingUser = false;
                
        boolean allowExternalStart = true;
        boolean allowExternalEnd = false;
        boolean allowAddress = true;
        boolean allowSlash = false; 
        boolean allowDash = false; 
        boolean allowDone = false;
        boolean allowUser = false; 
        boolean allowUUID = false; 
        
        InetSocketAddress [] externalAds = null; 
        InetSocketAddress [] publicAds = null; 
        InetSocketAddress [] privateAds = null; 
        
        byte [] UUID = null;
        
        String user = null;
        
        LinkedList<InetAddress> currentGlobal = new LinkedList<InetAddress>();
        LinkedList<InetAddress> currentLocal = new LinkedList<InetAddress>();
        
        while (st.hasMoreTokens()) { 
            
            String s = st.nextToken();
            
          //  System.out.println("Read: " + s);
            
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
                    allowUUID = false;
                    readingExternal = true;
                    break;
                
                case EXTERNAL_END:
                    
                    if (!allowExternalEnd) {
                        throw new IllegalArgumentException("Unexpected " 
                                + EXTERNAL_END + " in address(" 
                                + addressPort + ")");
                    }
                
                    allowExternalEnd = false; 
                    allowExternalStart = false;
                    allowAddress = true;
                    readingExternal = false;
                    
                    if (privateAds != null && privateAds.length > 0) { 
                        allowDone = true;
                        allowUUID = true;
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
                    allowUUID = false;
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
        
                case UUID_SEPERATOR: 
                    
                    if (!allowUUID) { 
                        throw new IllegalArgumentException("Unexpected " 
                                + UUID_SEPERATOR + " in address(" 
                                + addressPort + ")");
                    }

                    allowUUID = false;
                    allowDone = false;
                    allowUser = false;
                    readingUUID = true;
                    break;
                    
                    
                case USER_SEPERATOR: 
                    
                    if (!allowUser) { 
                        throw new IllegalArgumentException("Unexpected " 
                                + USER_SEPERATOR + " in address(" 
                                + addressPort + ")");
                    }

                    allowDone = false;
                    allowUser = false;
                    allowUUID = false;
                    readingUser = true;
                    break;
                    
                default:
                    // should never happen ? 
                    throw new IllegalArgumentException("Unexpected delimiter: " 
                            + delim + " in address(" + addressPort + ")");
                }

                
            } else if (readingUUID) { 
                
                UUID = NetworkUtils.stringToUUID(s);

                readingUUID = false;
                
                allowSlash = false;
                allowDone = true;
                allowUser = true;

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
                    externalAds = addToArray(externalAds, currentGlobal, port);
                } else { 
                    publicAds = addToArray(publicAds, currentGlobal, port);
                    privateAds = addToArray(privateAds, currentLocal, port);
                }
                
                readingPort = false;
                allowSlash = true;
                
                if (readingExternal) { 
                    allowExternalEnd = true;
                } else { 
                    allowDone = true;
                    allowUser = true;
                    allowUUID = true; 
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
        
        return new DirectSocketAddress(externalAds, publicAds, privateAds, UUID, user);
    }
    
    public static DirectSocketAddress getByAddress(String host, int port) throws UnknownHostException { 
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
    public static DirectSocketAddress merge(DirectSocketAddress s1, 
            DirectSocketAddress s2) {
        
        byte [] UUID = s1.UUID;
        
        if (s1.UUID == null) {
            UUID = s2.UUID;            
        } else if (s2.UUID != null) {               
            if (!Arrays.equals(s1.UUID, s2.UUID)) { 
                throw new IllegalArgumentException("Cannot merge two " +
                    "addresses with different UUIDs!");
            } 
        }
        
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
        
        return new DirectSocketAddress(
                merge(s1.externalAds, s2.externalAds), 
                merge(s1.publicAds, s2.publicAds), 
                merge(s1.privateAds, s2.privateAds), UUID, user);
    } 
    
    /**
     * Converts an array of SocketAddressSets to a String array.
     * 
     * @param s the array of {@link DirectSocketAddress}
     * @return a new String array containing the {@link String} representations 
     * of the {@link DirectSocketAddress}s, or <code>null</code> if 
     * s was <code>null</code> 
     */
    public static String [] convertToStrings(DirectSocketAddress [] s) {

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
     * @return a new array containing the {@link DirectSocketAddress}s 
     * or <code>null</code> if s was <code>null</code> 
     * @throws UnknownHostException when any of the Strings cannot be converted 
     * and ignoreProblems is false 
     */
    public static DirectSocketAddress [] convertToSocketAddressSet(String [] s, 
            boolean ignoreProblems) throws UnknownHostException {

        if (s == null) { 
            return null;
        }
        
        DirectSocketAddress [] result = new DirectSocketAddress[s.length];
        
        for (int i=0;i<s.length;i++) {            
            if (s[i] != null) { 
                if (ignoreProblems) {                 
                    try {                   
                        result[i] = DirectSocketAddress.getByAddress(s[i]);
                    } catch (UnknownHostException e) {
                        // ignore
                    }
                } else { 
                    result[i] = DirectSocketAddress.getByAddress(s[i]);
                } 
            }
        }
        
        return result;
    } 

    /**
     * Converts an array of Strings into an array of SocketAddressSets.  
     * 
     * @param s the array of {@link String} to convert
     * @return a new array containing the {@link DirectSocketAddress}s 
     * or <code>null</code> if s was <code>null</code> 
     * @throws UnknownHostException when any of the Strings cannot be converted 
     */
    public static DirectSocketAddress [] convertToSocketAddressSet(String [] s) 
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
    public boolean sameMachine(DirectSocketAddress other) { 
        return isCompatible(other, false);
    }

    /**
     * Returns if the other SocketAddressSet represents the same process as 
     * this one.   
     * 
     * @param other the SocketAddressSet to compare to
     * @return if both SocketAddressSets represent the same process
     */
    public boolean sameProcess(DirectSocketAddress other) {
        return isCompatible(other, true);
    }
    
    /**
     * Returns if the two SocketAddressSets represent the same machine. 
     * 
     */
    public static boolean sameMachine(DirectSocketAddress a, DirectSocketAddress b) { 
        return a.sameMachine(b);
    }

    /**
     * Returns if the two SocketAddressSets represent the same process. 
     * 
     */
    public static boolean sameProcess(DirectSocketAddress a, DirectSocketAddress b) {
        return a.sameProcess(b);
    }

    public int numberOfAddresses() {
        return externalAds.length + publicAds.length + privateAds.length;
    }

  
}
