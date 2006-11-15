package smartsockets.virtual;


import java.io.Serializable;
import java.net.UnknownHostException;

import smartsockets.direct.SocketAddressSet;

public class VirtualSocketAddress implements Serializable { 

    private static final long serialVersionUID = 3340517955293464166L;
    
    private final SocketAddressSet machine;
    private final int port;
    
    // This hub field is a hint of the location of the machine. It may be null
    // or change over time (e.g., when a hub crashes and a machine registers 
    // at another hub).     
    private final SocketAddressSet hub;
    
    // This field indicates which 'virtual cluster' the machine is part of.
    private final String cluster;

    public VirtualSocketAddress(SocketAddressSet machine, int port) {
        this(machine, port, null, null);
    }
    
    public VirtualSocketAddress(SocketAddressSet machine,
            int port, SocketAddressSet hub, String cluster) {
        
        this.hub = hub;
        this.machine = machine;
        this.port = port;
        this.cluster = cluster;
    }
    
    /**
     * Construct a new VirtualSocketAddress starting from a String with the 
     * following format: 
     * 
     *   MACHINEADDRESS:PORT[@MACHINEADDRESS][#CLUSTER]
     *   
     * The '@MACHINEADDRESS' part is optional and indicates the hub where the 
     * machine can be found. The '#CLUSTER' part is also optional and indicates
     * which virtual cluster the machine belongs to. 
     *  
     * @param address 
     * @throws UnknownHostException 
     */
    public VirtualSocketAddress(String address) throws UnknownHostException { 
                        
        int index1 = address.lastIndexOf('@');
        int index2 = address.lastIndexOf('#');                

        if (index2 < index1) {
            // The hub is after the cluster (or cluster does not exist).
            hub = new SocketAddressSet(address.substring(index1+1));
            
            if (index2 != -1) { 
                cluster = address.substring(index2+1, index1);
                address = address.substring(0, index2);
            } else {
                cluster = null;
                address = address.substring(0, index1);
            }
            
        } else if (index2 > index1){
            // The hub is before the cluster.
            cluster = address.substring(index2+1);
            
            if (index1 != -1) {            
                hub = new SocketAddressSet(address.substring(index1+1, index2));
                address = address.substring(0, index1);
            } else { 
                address = address.substring(0, index2);
                hub = null;
            }
        } else { 
            // both index1 and index2 are '-1'
            cluster = null;
            hub = null;
        }
            
        int index = address.lastIndexOf(':');

        if (index == -1) { 
            throw new IllegalArgumentException("String does not contain " 
                    + "VirtualSocketAddress!");
        }
                
        machine = new SocketAddressSet(address.substring(0, index));
        port = Integer.parseInt(address.substring(index+1));                        
    }

    public VirtualSocketAddress(String machine, int port) 
        throws UnknownHostException {
        
        this(new SocketAddressSet(machine), port, null, null);
    }

    public VirtualSocketAddress(String hub, String machine, int port) 
        throws UnknownHostException {    
        
        this(new SocketAddressSet(machine), port, new SocketAddressSet(hub), null);
    }
    
    public SocketAddressSet hub() { 
        return hub;
    }
    
    public SocketAddressSet machine() { 
        return machine;
    }

    public String cluster() { 
        return cluster;
    }
    
    public int port() { 
        return port;
    }    
    
    public String toString() {         
        return machine.toString() + ":" + port 
            + (hub == null ? "" : ("@" + hub.toString())) 
            + (cluster == null ? "" : ("#" + cluster)); 
    }
    
    public boolean equals(Object other) { 
     
        if (this == other) {            
            return true;
        }
        
        if (other == null) { 
            return false;
        }
                
        if (!(other instanceof VirtualSocketAddress)) {
            return false;
        }
        
        // Now compare the addresses. Note that the hub field is not compared, 
        // since it is only a hint of the location of the machine. It may be 
        // null in some cases or contain different values if the machine is 
        // registered at multiple proxies.       
        VirtualSocketAddress tmp = (VirtualSocketAddress) other;
        
        // The ports must be the same. 
        if (port != tmp.port) { 
            return false;
        }
        
        // The machine must be the same 
        return machine.equals(tmp.machine);
    }
    
    public int hashCode() {
        return machine.hashCode() ^ port;        
    }
    
    public static VirtualSocketAddress partialAddress(String hostname, 
            int realport, int virtualport) throws UnknownHostException {
     
        return new VirtualSocketAddress(
                new SocketAddressSet(hostname, realport), virtualport);
    }
    
    public static VirtualSocketAddress partialAddress(String hostname, 
            int port) throws UnknownHostException {     
        return partialAddress(hostname, port, port);
    }    
    
    
    
}
