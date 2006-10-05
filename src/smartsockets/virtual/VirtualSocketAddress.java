package smartsockets.virtual;


import java.io.Serializable;
import java.net.UnknownHostException;

import smartsockets.direct.SocketAddressSet;

public class VirtualSocketAddress implements Serializable { 

    private static final long serialVersionUID = 3340517955293464166L;
    
    private final SocketAddressSet machine;
    private final int port;
    
    // This proxy field is a hint of the location of the machine. It may be null
    // or change over time (e.g., when a proxy crashes and a machine registers 
    // at another proxy).     
    private final SocketAddressSet proxy;
    
    // This field indicates which 'virtual cluster' the machine is part of.
    // private final String cluster;

    public VirtualSocketAddress(SocketAddressSet machine, int port) {
        this(machine, port, null);
    }
    
    public VirtualSocketAddress(SocketAddressSet machine,
            int port, SocketAddressSet proxy) {
        
        this.proxy = proxy;
        this.machine = machine;
        this.port = port;
    }
    
    /**
     * Construct a new VirtualSocketAddress starting from a String with the 
     * following format: 
     * 
     *   MACHINEADDRESS:PORT[@MACHINEADDRESS]
     *   
     * The last part of the address '@MACHINEADDRESS' is optional and indicates  
     * the proxy where the machine can be found. 
     *  
     * @param address 
     * @throws UnknownHostException 
     */
    public VirtualSocketAddress(String address) throws UnknownHostException { 
        
        int index = address.lastIndexOf('@');
        
        if (index != -1) {
            proxy = new SocketAddressSet(address.substring(index+1));
            address = address.substring(0, index);
        } else { 
            proxy = null;
        }
                
        index = address.lastIndexOf(':');

        if (index == -1) { 
            throw new IllegalArgumentException("String does not contain " 
                    + "VirtualSocketAddress!");
        }
                
        machine = new SocketAddressSet(address.substring(0, index));
        port = Integer.parseInt(address.substring(index+1));                        
    }

    public VirtualSocketAddress(String machine, int port) 
        throws UnknownHostException {
        
        this(new SocketAddressSet(machine), port, null);
    }

    public VirtualSocketAddress(String proxy, String machine, int port) 
        throws UnknownHostException {    
        
        this(new SocketAddressSet(machine), port, new SocketAddressSet(proxy));
    }

    public SocketAddressSet proxy() { 
        return proxy;
    }
    
    public SocketAddressSet machine() { 
        return machine;
    }
    
    public int port() { 
        return port;
    }    
    
    public String toString() { 
        return machine.toString() + ":" + port;       
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
        
        // Now compare the addresses. Note that the proxy field is not compared, 
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
}
