package ibis.smartsockets.util;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.Comparator;
 
public class AddressSorter implements Serializable, Comparator<InetAddress> {
    
    private static final long serialVersionUID = -2003381888113229585L;

    /** 
     * Orders two addresses based on their length. If both addresses have 
     * the same length, the addresses are ordered based on comparing their raw 
     * bytes values.
     *      
     * @param i1 an InetAddress
     * @param i2 an InetAddress
     * @return int value indicating the order of the addresses 
     */
    private int compareAddress(InetAddress i1, InetAddress i2) { 
        
        // We have a preference for the shortest address (i.e., IPv4)
        byte [] tmp1 = i1.getAddress();
        byte [] tmp2 = i2.getAddress();
        
        if (tmp1.length != tmp2.length) {
            return tmp2.length - tmp1.length;
        }
        
        for (int i=0;i<tmp1.length;i++) { 
            if (tmp1[i] != tmp2[i]) {
                
                int t1 = tmp1[i] & 0xFF;
                int t2 = tmp2[i] & 0xFF;
                return t2 - t1;
            }                
        }
        
        return 0;
    }

    /** 
     * Gives the address a score based on the class it belongs to. The more 
     * general the class, the lower the score.
     *      
     * @param ina the address to score
     * @return score given to the address
     */
    private int score(InetAddress ina) { 
                
        if (ina.isLoopbackAddress()) {
            return 8;
        }
        
        if (ina.isLinkLocalAddress()) {
            return 6;
        }
              
        if (ina.isSiteLocalAddress()) { 
            return 4;
        }
                
        // It's a 'normal' global IP
        return 2;
    }
    
    /**
     * This compares two InetAddresses. 
     * 
     * Both parameters should either be an InetAddress or a InetSocketAddress. 
     * It starts by putting the addresses in one of the following classes: 
     *
     *   1. Global 
     *   2. Site Local
     *   3. Link Local
     *   4. Loopback
     *
     * When the addresses end up in the same class, they are sorted by length. 
     * (shortest first, so IPv4 is preferred over IPv6). 
     * If their length is the same, the individual bytes are compared. 
     * The address with the lowest byte values comes first.     
     */    
    public int compare(InetAddress i1, InetAddress i2) {

        /*
        InetAddress i1 = null;
        InetAddress i2 = null;
        
        
        if (o1 instanceof InetSocketAddress) { 
            i1 = ((InetSocketAddress) o1).getAddress();
            i2 = ((InetSocketAddress) o2).getAddress();
        } else { 
            i1 = (InetAddress) o1;
            i2 = (InetAddress) o2;
        }
    */
        
        int score1 = score(i1);
        int score2 = score(i2);            
        
        int result = 0;
        
        if (score1 == score2) { 
            result = compareAddress(i1, i2);
        } else { 
            result = score1 - score2;
        } 
        
        /*if (PREFER_LOCAL) {
            result = -result;
        }*/
        
        return result;
    }         
}