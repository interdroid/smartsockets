package ibis.connect.virtual;

import ibis.connect.direct.SocketAddressSet;

import java.io.Serializable;
import java.net.UnknownHostException;

public class VirtualSocketAddress implements Serializable { 

    private static final long serialVersionUID = 3340517955293464166L;
    
    private final SocketAddressSet machine;
    private final int port;
    
    public VirtualSocketAddress(SocketAddressSet machine, int port) { 
        this.machine = machine;
        this.port = port;      
    }
    
    /**
     * Construct a new IbisSocketAddress starting from a String with the 
     * following format: 
     * 
     *   MACHINEADDRESS:PORT
     *  
     * @param address 
     * @throws UnknownHostException 
     */
    public VirtualSocketAddress(String address) throws UnknownHostException { 
        
        int close = address.lastIndexOf(':');

        if (close == -1) { 
            throw new IllegalArgumentException("String does not contain " 
                    + "IbisSocketAddress!");
        }
        
        machine = new SocketAddressSet(address.substring(0, close));
        port = Integer.parseInt(address.substring(close+1));                        
    }

    public VirtualSocketAddress(String machine, int port) throws UnknownHostException {         
        this(new SocketAddressSet(machine), port);
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
        
        VirtualSocketAddress tmp = (VirtualSocketAddress) other;
        
        if (port != tmp.port) { 
            return false;
        }

        return machine.equals(tmp.machine);
    }
    
    public int hashCode() {         
        // TODO: improve
        return machine.hashCode() ^ port;        
    }
}
