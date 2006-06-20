package ibis.connect.direct;

import ibis.connect.util.AddressSorter;
import ibis.connect.util.NetworkUtils;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.StringTokenizer;


/**
 * This class represents an set of IP addresses. 
 * 
 * Many machines found in Grid systems contain multiple network interfaces and 
 * can thus be reached using several different IP addresses. Often, each of 
 * these IP addresses can only be reached from a certain range of source IPs. 
 * Therefore, using only a single IP address to identify a machine may be 
 * insufficient to reach it in all cases. This may result in problems, 
 * especially when IP addresses are forwarded from one machines to another.  
 * 
 * Example: 
 * 
 *  A cluster that consists of a frontend machine and a number of compute nodes.
 *  The frontend is connected to two networks; the internet using a 'public' IP 
 *  address and the local network using a 'site local' address. The compute 
 *  nodes are only connected to the local network an have a 'site local' 
 *  address. Therefore, to reach the frontend machine, either the 'public' or 
 *  'site local' address must be used, depending on the location of the machine 
 *  that is trying to connect to the server. 
 *  
 * This class encapsulates a set of IP addresses, and can be used to represent 
 * the different IP addresses of a machine. This way, the set can be forwarded 
 * from one machine to another, and still contains a useful contact address.  
 * 
 * @author Jason Maassen
 * @version 1.0 Dec 19, 2005
 * @since 1.0
 * 
 */
public class IPAddressSet implements Serializable {
    
    private static final long serialVersionUID = 8548119455369383377L;
    
    // Size of the byte representations of 'standard' InetAddresses.
    private static final int LENGTH_IPv4 = 4;
    private static final int LENGTH_IPv6 = 16;
       
    // A object capable of sorting 'standard' InetAddresses.
    protected static final AddressSorter SORTER = new AddressSorter();
       
    // Cache for the InetXAddress representing this machine.
    private static IPAddressSet localHost;    
                       
    // The byte representation of this InetAddressSet. 
    private transient byte [] codedForm;
    
    // The actual InetAddresses
    protected final InetAddress [] addresses;

    private IPAddressSet(InetAddress [] addresses, byte [] codedForm) {        
        this.addresses = addresses;        
        this.codedForm = codedForm;
    }
            
    private IPAddressSet(InetAddress [] addresses) {
        this(addresses, null);
        // Note: codeForm will be created on demand.
    }
    
    /**
     * Returns a byte representation of this InetAddressSet.
     * 
     * This representation is either contains the 4 or 16 bytes of a single 
     * InetAddress, or it has the form (N (SA)*) where:
     *  
     *   N is the number of addresses that follow (1 byte) 
     *   S is the length of the next address (1 byte)
     *   A is an InetAddress (4 or 16 bytes)
     * 
     * @return the bytes
     */
    public byte[] getAddress() {
        if (codedForm == null) {
            if (addresses.length == 1) {
                // It there is just a single address, we directly return the 
                // byte representation of this address. 
                codedForm = addresses[0].getAddress();
            } else {
                // There are more addresses, so we first calucate the length of
                // the coded form. Note that this assumes that an InetAddressSet
                // always contains less than 256 addresses, and each address is
                // shorter that 256 bytes.        
                int len = 1;
            
                for (int i=0;i<addresses.length;i++) {
                    len += 1 + addresses[i].getAddress().length;                    
                }
                
                // We now know the size, so create the array and fill it.                 
                codedForm = new byte[len];
                        
                int index = 0;
                
                codedForm[index++] = (byte) addresses.length;
                
                for (int i=0;i<addresses.length;i++) {
                    byte [] tmp = addresses[i].getAddress();
                    codedForm[index++] = (byte) tmp.length;
                    System.arraycopy(tmp, 0, codedForm, index, tmp.length);
                    index += tmp.length;
                }            
            }
        }
        
        return codedForm;
    }
        
    /**
     * Returns an array of all InetAddresses encapsulated by this object. 
     * 
     * @return array of InetAddresses.
     */
    public InetAddress [] getAddresses() { 
        return addresses;
    }
        
    /**
     * Checks if this InetAddressSet contains at least one global InetAddress. 
     * 
     * @return true if this InetAddressSet contains at least one global address, 
     * false otherwise.  
     */
    public boolean containsGlobalAddress() {        
        return NetworkUtils.containsGlobalAddress(addresses);        
    }
    
    /* (non-Javadoc)
     * @see java.net.InetAddress#hashCode()
     */
    public int hashCode() {
        int code = 0;
        
        for (int i=0;i<addresses.length;i++) { 
            code ^= addresses[i].hashCode();
        }
        
        return code;
    }

    /* (non-Javadoc)
     * @see java.net.InetAddress#equals(java.lang.Object)
     */
    public boolean equals(Object other) {
        
        // Check pointers
        if (this == other) { 
            return true;
        }
        
        // Check type. 
        if (!(other instanceof IPAddressSet)) {
            return false;
        }
        
        IPAddressSet tmp = (IPAddressSet) other;
        
        // Compare lengths of addresses array
        if (addresses.length != tmp.addresses.length) { 
            return false;
        }

        // Finally compare addresses. Note that the order should 
        // be the same.        
        for (int i=0;i<addresses.length;i++) {           
            if (!addresses[i].equals(tmp.addresses[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    /* (non-Javadoc)
     * @see java.net.InetAddress#toString()
     */
    public String toString() {
        StringBuffer tmp = new StringBuffer("");
        
        for (int i=0;i<addresses.length;i++) { 
            tmp.append(NetworkUtils.ipToString(addresses[i]));
            
            if (i != addresses.length-1) { 
                tmp.append("/");
            }            
        }
        
        return tmp.toString();
    }

    /**
     * Create a new InetAddressSet by adding an InetAddress to an existing one. 
     * 
     * @param address source InetAddressSet
     * @param add InetAddress to add
     * @return new InetAddressSet  
     */
    public static IPAddressSet add(IPAddressSet address, InetAddress add) { 
        int len = address.addresses.length;                
        InetAddress [] tmp = new InetAddress[len+1];                
        tmp[0] = add;
        System.arraycopy(address.addresses, 0, tmp, 1, len);
        return getFromAddress(tmp);                
    }

    /**
     * Create a new InetAddressSet by combing two existing ones. 
     * 
     * @param a1 source InetAddressSet
     * @param a2 source InetAddressSet
     * @return new InetAddressSet containing the combination of the two. 
     */    
    public static IPAddressSet merge(IPAddressSet a1, IPAddressSet a2) { 
                       
        if (a1 == null) { 
            return a2;
        }
        
        if (a2 == null) { 
            return a1;
        }
        
        InetAddress [] tmp = new InetAddress[a1.addresses.length + 
                                             a2.addresses.length];                
        
        System.arraycopy(a1.addresses, 0, tmp, 0, a1.addresses.length);
        System.arraycopy(a2.addresses, 0, tmp, a1.addresses.length, 
                a2.addresses.length);
        
        return getFromAddress(tmp);                
    }
   
    /**
     * Create a new InetAddressSet by combing an existing one and an InetAddress. 
     * 
     * @param a1 source InetAddressSet
     * @param a2 source InetAddress
     * @return new InetAddressSet containing the combination of the two. 
     */    
    public static IPAddressSet merge(IPAddressSet a1, InetAddress a2) { 
         
        if (a1 == null && a2 != null) { 
            return new IPAddressSet(new InetAddress[] { a2 });
        }
        
        if (a2 == null) { 
            return a1;
        }
        
        InetAddress [] tmp = new InetAddress[a1.addresses.length + 1]; 
        
        System.arraycopy(a1.addresses, 0, tmp, 0, a1.addresses.length);
        tmp[tmp.length-1] = a2;

        return getFromAddress(tmp);                
    }   
    
    /**
     * Create a new InetAddressSet from a byte array. 
     * 
     * The byte array may either contain the byte representation of an 
     * InetAddress (IPv4 or IPv6) or the byte representation of an 
     * InetAddressSet which has the form (N (SA)*) where:
     *  
     *   N is the number of addresses that follow (byte) 
     *   S is the length of the next address (byte)
     *   A is an InetAddress
     * 
     * @param bytes input byte array
     * @return new InetAddressSet  
     * @throws UnknownHostException 
     */        
    public static IPAddressSet getByAddress(byte [] bytes) 
        throws UnknownHostException {  
    
        InetAddress [] addresses = null;
        
        if (bytes.length == LENGTH_IPv4 || bytes.length == LENGTH_IPv6) {
            // the byte [] contains a 'normal' InetAddress
            addresses = new InetAddress[1];            
            addresses[0] = InetAddress.getByAddress(bytes);
        } else {
            // the byte [] contains a 'extended' InetAddress       
            int index = 0;        
            int len = bytes[index++];
            
            addresses = new InetAddress[len];
            
            byte [] tmp = null;
            
            for (int i=0;i<addresses.length;i++) {
                
                int size = bytes[index++];
                
                if (tmp == null || tmp.length != size) { 
                    tmp = new byte[size];
                }            
                
                System.arraycopy(bytes, index, tmp, 0, size);
                addresses[i] = InetAddress.getByAddress(tmp);
                index += size;
            }
        }
        
        return new IPAddressSet(addresses, bytes);
    }

    /**
     * Create a new InetAddressSet from a String. 
     * 
     * The String must have the form A ('/'A)* where: A is an String 
     * representation of a InetAddress. 
     * 
     * @param address the InetAddressSet as a String
     * @return new InetAddressSet  
     * @throws UnknownHostException
     */            
    public static IPAddressSet getFromString(String address) 
        throws UnknownHostException {
    
        StringTokenizer st = new StringTokenizer(address, "/");
        
        int len = st.countTokens();
        
        InetAddress [] addresses = new InetAddress[len];
        
        for (int i=0;i<len;i++) { 
            addresses[i] = InetAddress.getByName(st.nextToken());
        }
        
        return new IPAddressSet(sort(addresses));
    }
 
    /**
     * Create a new InetAddressSet from an array of InetAddress objects.   
     * 
     * @param addresses the InetAddresses
     * @return new InetAddressSet  
     */               
    public static IPAddressSet getFromAddress(InetAddress [] addresses) {
        return new IPAddressSet(sort(addresses));
    }
    
    /**
     * Create a new InetAddressSet that represents this host.    
     * 
     * All addresses that can be found locally will be included in the 
     * InetAddressSet. Note that this does not necessarilly include the 
     * 'external' address of the network when NAT is used.   
     * 
     * @return new InetAddressSet representing this host.   
     */               
    public static IPAddressSet getLocalHost() { 
        
        if (localHost == null) {         
            // Get all the local addresses, including IPv6 ones, but excluding 
            // loopback addresses, and sort them.
            InetAddress [] addresses = 
                sort(NetworkUtils.getAllHostAddresses(true, false));    
                           
            DirectSocketFactory.logger.info("Result after sorting: ");
            DirectSocketFactory.logger.info(" " 
                    + NetworkUtils.ipToString(addresses));
        
            if (!NetworkUtils.containsGlobalAddress(addresses)) {
                DirectSocketFactory.logger.info(" Result does NOT contain " +
                        "global address!");
                // TODO: Try to find the external address here ?
            } else { 
                DirectSocketFactory.logger.info(" Result contains global " +
                        "address!");
            }
                
            localHost = new IPAddressSet(addresses);
        }
        
        return localHost;            
    }
          
    /**
     * Sorts an array of InetAddress object according to the order defined by 
     * the InetAddressSorter. 
     * 
     * Note that this method changes the content of the parameter array and, for 
     * convenience, also returns a reference to this array.
     * 
     * @param in the array that must be sorted. 
     * @return reference to the sorted array parameter. 
     */
    private static InetAddress [] sort(InetAddress [] in) { 
        
        if (in != null && in.length > 1) { 
            Arrays.sort(in, SORTER);
        }
        return in;
    }        
}
