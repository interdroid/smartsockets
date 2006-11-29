package smartsockets.direct;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

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
    
    private transient IPAddressSet address;      
    private transient InetSocketAddress [] sas;
    
    private transient byte [] codedForm = null;
    private transient String toStringCache = null;
            
    private SocketAddressSet(IPAddressSet as, InetSocketAddress [] sas) {
        this.address = as;
        this.sas = sas;        
    }
    
    /**
     * Construct a new IbisSocketAddress, starting from a byte encode version
     * 
     * @param address The InetSocketAddress.
     * @throws UnknownHostException 
     */    
    public SocketAddressSet(byte [] coded) throws UnknownHostException {
        readFromBytes(coded);
    } 
    
    private void readFromBytes(byte [] coded) throws UnknownHostException {
        
        try { 
            
        byte [] tmp4 = null;
        byte [] tmp16 = null;
        
        int index = 0;
        int len = coded[index++] & 0xFF;
        
        sas = new InetSocketAddress[len];
        
        InetAddress [] tmp = new InetAddress[len];
        
        System.out.println("Got address with " + len + " entries");
        
        for (int i=0;i<len;i++) { 
            
            int adlen = coded[index++] & 0xFF;
            
            int port = 0;
            
            System.out.println("Got address " + i + " of length " + adlen);
            
            if (adlen == 4) { 
                // IPv4
                if (tmp4 == null) { 
                    tmp4 = new byte[4];
                }
                
                System.arraycopy(coded, index, tmp4, 0, 4);
                index += 4;
                
                port = (coded[index++] & 0xFF);
                port |= (coded[index++] & 0xFF) << 8;
                tmp[i] = InetAddress.getByAddress(tmp4);
            } else { 
                // IPv6
                if (tmp16 == null) { 
                    tmp16 = new byte[16];
                }
                
                System.arraycopy(coded, index, tmp16, 0, 16);
                index += 16;
                
                port = (coded[index++] &0xFF) | (coded[index++] & 0xFF) << 8;
                tmp[i] = InetAddress.getByAddress(tmp16);
            }
            
            sas[i] = new InetSocketAddress(tmp[i], port);
        } 
        
        address = IPAddressSet.getFromAddress(tmp);
  
        this.codedForm = coded;
        
        System.out.println("SocketAddressSet.fromBytes -> " + sas + " " + address);
        
        } catch (Throwable e) { 
            System.out.println("fromBytes failed!!!");
            e.printStackTrace();
            System.exit(1);
        }
        
    }
    
    /**
     * Construct a new IbisSocketAddress, using InetAddress and a port number.
     * 
     * a valid port value is between 0 and 65535. A port number of zero will 
     * let the system pick up an ephemeral port in a bind operation.
     *
     * A null address will assign the wildcard address. 
     * 
     * @param address The InetAddress.
     * @param port The port number.
     */    
    public SocketAddressSet(InetAddress address, int port) {
        this(IPAddressSet.getFromAddress(new InetAddress[] {address}), port);        
    }        
    
    /**
     * Construct a new IbisSocketAddress, using InetSocketAddress
     * 
     * @param address The InetSocketAddress.
     */    
    public SocketAddressSet(InetSocketAddress address) {
        this(address.getAddress(), address.getPort());
    }        
    
    
    /**
     * Construct a new IbisSocketAddress, using an IbisInetAddress and a port 
     * number.
     * 
     * a valid port value is between 0 and 65535. A port number of zero will 
     * let the system pick up an ephemeral port in a bind operation.
     *
     * A null address will assign the wildcard address. 
     * 
     * @param address The IbisInetAddress.
     * @param port The port number.
     */
    public SocketAddressSet(IPAddressSet address, int port) {
        
        if (port < 0 || port > 65535) { 
            throw new IllegalArgumentException("Port out of range");
        }
                
        if (address == null) { 
            this.address = IPAddressSet.getLocalHost();
        } else {             
            this.address = address;
        }
    
        int len = address.addresses.length;
        sas = new InetSocketAddress[len];
        
        for (int i=0;i<len;i++) { 
            sas[i] = new InetSocketAddress(address.addresses[i], port);
        }
        
        toString();
    }
        
    /**
     * Construct a new IbisSocketAddress, using an IbisInetAddress and an array 
     * of ports.
     * 
     * A valid port value is between 1 and 65535. A port number of zero is not 
     * allowed.
     *
     * A null address will assign the wildcard address. 
     * 
     * @param address The IbisInetAddress.
     * @param port The port number.
     */
    public SocketAddressSet(IPAddressSet address, int [] port) {
                               
        if (address == null) { 
            this.address = IPAddressSet.getLocalHost();
        } else {             
            this.address = address;
        }
    
        if (port.length != address.addresses.length) { 
            throw new IllegalArgumentException("Number of ports does not match" 
                    + "number of addresses");
        }        
        
        int len = address.addresses.length;
        sas = new InetSocketAddress[len];
        
        for (int i=0;i<len;i++) { 
            if (port[i] <= 0 || port[i] > 65535) { 
                throw new IllegalArgumentException("Port["+i+"] out of range");
            }
            
            sas[i] = new InetSocketAddress(address.addresses[i], port[i]);
        }
        
        toString();
    }
    
    /**
     * Construct a new IbisSocketAddress from a String representation of an 
     * IPAddressSet and a port number.
     * 
     * A valid port value is between 0 and 65535. A port number of zero will let
     * the system pick up an ephemeral port in a bind operation. 
     * 
     * @param address The String representation of an IbisInetAddress.
     * @param port The port number.
     * @throws UnknownHostException
     */
    public SocketAddressSet(String address, int port) 
        throws UnknownHostException {        
        this(IPAddressSet.getFromString(address), port);        
    }

    /**
     * Construct a new IbisSocketAddress from a String representation of a 
     * IbisSocketAddress. 
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
     * @param addressPort The String representation of a IbisSocketAddress.
     * @throws UnknownHostException
     */
    public SocketAddressSet(String addressPort) throws UnknownHostException { 

        int start = 0;        
        boolean done = false;
        
        IPAddressSet addr = null;
        int [] ports = new int[0];
                
        while (!done) {
            // We start by selecting a single address and port number from the 
            // string, starting where we ended the last time.             
            int colon = addressPort.indexOf(IP_PORT_SEPERATOR, start);                
            int slash = addressPort.indexOf(ADDRESS_SEPERATOR, colon+1);
        
            // We now seperate the 'address' and 'port' parts. If there is a '/'            
            // behind the port there is an other address following it. If not, 
            // this is the last time we go through the loop.
            String a = addressPort.substring(start, colon);
            String p;             
            
            if (slash == -1) {
                p = addressPort.substring(colon+1);
                done = true;
            } else { 
                p = addressPort.substring(colon+1, slash);
                start = slash+1;
            }
        
            // Now convert the address and port strings to real values ...
            IPAddressSet tA = IPAddressSet.getFromString(a);
            int tP = Integer.parseInt(p);
     
            // ... do a sanity check on the port value ...
            if (tP <= 0 || tP > 65535) { 
                throw new IllegalArgumentException("Port out of range: " + tP);
            }
            
            // ... and merge the result into what was already there. 
            addr = IPAddressSet.merge(addr, tA);
            ports = add(ports, tP, tA.addresses.length);
        }
        
        // Finally, store the result in the object fields. 
        address = addr;
        
        int len = addr.addresses.length;
        sas = new InetSocketAddress[len];
        
        for (int i=0;i<len;i++) { 
            sas[i] = new InetSocketAddress(addr.addresses[i], ports[i]);
        }
        
        toString();
    }
   
    /**
     * This method returns the byte coded form of the SocketAddressSet.
     * 
     * This representation is either contains the 6 or 18 bytes of a single 
     * InetAddress + port number, or it has the form (N (SAP)*) where:
     *  
     *   N is the number of addresses that follow (1 byte) 
     *   S is the length of the next address (1 byte)
     *   A is an InetAddress (4 or 16 bytes)
     *   P is the port number (2 bytes)
     * 
     * @return the bytes
     */
    
    public byte [] getAddress() { 
        
        if (codedForm == null) {
            
            // First calculate the size of the address n bytes....
            int len = 1;
            
            for (InetSocketAddress s : sas) { 
                
                if (s.getAddress() instanceof Inet4Address) {
                    len += 1 + 4 + 2;                    
                } else { 
                    len += 1 + 16 + 2;
                }
            }
            
            // Now encode it...
            codedForm = new byte[len];
            
            int index = 0;
            
            codedForm[index++] = (byte) (sas.length & 0xFF);
            
            for (InetSocketAddress s : sas) { 
                byte [] tmp = s.getAddress().getAddress();
                codedForm[index++] = (byte) (tmp.length & 0xFF);
                System.arraycopy(tmp, 0, codedForm, index, tmp.length);                
                index += tmp.length;

                int port = s.getPort();
                codedForm[index++] = (byte) (port & 0xFF);
                codedForm[index++] = (byte) ((port >> 8) & 0xFF);
            }  
        }
        
        return codedForm;
    }
    
    /**
     * This method appends a value a specified number of times to an existing 
     * int array. A new array is returned.
     * 
     * @param orig the original array
     * @param value the value to append
     * @param repeat the number of times the value should be appended.
     * @return a new array 
     */
    private int [] add(int [] orig, int value, int repeat) { 
        int [] result = new int[orig.length+repeat];
        System.arraycopy(orig, 0, result, 0, orig.length);
        Arrays.fill(result, orig.length, result.length, value);        
        return result;
    }    
    
    /**
     * Gets the InetAddressSet.
     * 
     * @return the InetAddressSet.
     */
    public IPAddressSet getAddressSet() { 
        return address;        
    }
    
    /**
     * Gets the SocketAddresses.
     * 
     * @return the ports.
     */
    public InetSocketAddress [] getSocketAddresses() { 
        return sas; 
    } 
        
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        // TODO: improve 
        return address.hashCode(); 
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
      
        // System.out.println("SocketAdressSet.equals: ");
        // System.out.println("arrays : " + Arrays.equals(sas, tmp.sas));
        // System.out.println("address: " + address.equals(tmp.address));
        
        // Finally, compare ports and addresses
        
        // TODO: this is only correct if the addresses are exactly the same.
        // So how do we handle partial addresses ?         
        for (int i=0;i<sas.length;i++) { 
            InetSocketAddress a = sas[i];

            boolean found = false;

            for (int j=0;j<tmp.sas.length;j++) {               
                if (a.equals(tmp.sas[j])) {
                    found = true;
                    break;
                }
            }

            if (!found) { 
                return false;
            }
        }
        
        return true;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        
        if (toStringCache == null) { 
            StringBuffer b = new StringBuffer();

            final int len = address.addresses.length; 

            for (int i=0;i<len;i++) { 

                b.append(NetworkUtils.ipToString(address.addresses[i]));

                if (i < len-1) {                
                    if (sas[i].getPort() != sas[i+1].getPort()) { 
                        b.append(IP_PORT_SEPERATOR);
                        b.append(sas[i].getPort());
                    } 

                    b.append(ADDRESS_SEPERATOR);
                } else { 
                    b.append(IP_PORT_SEPERATOR);
                    b.append(sas[i].getPort());
                }
            }

            toStringCache = b.toString();
        } 
        
        return toStringCache;
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
        
        IPAddressSet as = IPAddressSet.merge(s1.address, s2.address);
                       
        InetSocketAddress [] sa = 
            new InetSocketAddress[s1.sas.length + s2.sas.length];
        
        System.arraycopy(s1.sas, 0, sa, 0, s1.sas.length);
        System.arraycopy(s2.sas, 0, sa, s1.sas.length, s2.sas.length);
        
        Arrays.sort(sa, IPAddressSet.SORTER);
        
        return new SocketAddressSet(as, sa);
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
                        result[i] = new SocketAddressSet(s[i]);
                    } catch (UnknownHostException e) {
                        // ignore
                    }
                } else { 
                    result[i] = new SocketAddressSet(s[i]);
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
        return address.equals(other.address);
    }

    /**
     * Returns if the other SocketAddressSet represents the same process as 
     * this one.   
     * 
     * @param other the SocketAddressSet to compare to
     * @return if both SocketAddressSets represent the same process
     */
    public boolean sameProcess(SocketAddressSet other) {
        return equals(other);
    }
    
    /**
     * Returns if the two SocketAddressSets represent the same machine. 
     * 
     */
    public static boolean sameMachine(SocketAddressSet a, SocketAddressSet b) { 
        return a.sameMachine(b);
    }

    /**
     * Returns if the two SocketAddressSets represent the same process. 
     * 
     */
    public static boolean sameProcess(SocketAddressSet a, SocketAddressSet b) {
        return a.sameProcess(b);
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

    public boolean isCompatible(SocketAddressSet target) {
        return equals(target);
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        
        byte [] a = getAddress();
        
        out.writeInt(a.length);
        out.write(getAddress());
        
        System.out.println("Writing SocketAddressSet (" + a.length + "): " + sas + " " + address);
        
    }
    
    private void readObject(ObjectInputStream in) throws IOException {
        
        int len = in.readInt();
        byte [] tmp = new byte[len];
        
        System.out.println("Read SocketAddressSet (" + len + "): " + sas + " " + address);
        
        in.readFully(tmp);       
        readFromBytes(tmp);   
    }
}
