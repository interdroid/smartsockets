package smartsockets.virtual;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.net.UnknownHostException;

import smartsockets.direct.SocketAddressSet;
import smartsockets.util.TransferUtils;

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
    
    // Cache for the coded form of this address
    private transient byte [] codedForm;

    public VirtualSocketAddress(DataInput in) throws IOException {

        int mlen = in.readShort();         
        int hlen = in.readShort();
        int clen = in.readShort();

        byte [] m = new byte[mlen];        
        in.readFully(m);        
        machine = SocketAddressSet.fromBytes(m);        
        
        port = in.readInt();
        
        if (hlen > 0) { 
            byte [] h = new byte[hlen];        
            in.readFully(h);        
            hub = SocketAddressSet.fromBytes(m);        
        } else { 
            hub = null;
        }
        
        if (clen > 0) { 
            byte [] c = new byte[clen];
            in.readFully(c);
            cluster = new String(c);
        } else {
            cluster= null;
        }     
    }
        
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
            hub = SocketAddressSet.getByAddress(address.substring(index1+1));
            
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
                hub = SocketAddressSet.getByAddress(address.substring(index1+1, index2));
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
                
        machine = SocketAddressSet.getByAddress(address.substring(0, index));
        port = Integer.parseInt(address.substring(index+1));                        
    }

    public VirtualSocketAddress(String machine, int port) 
        throws UnknownHostException {
        
        this(SocketAddressSet.getByAddress(machine), port, null, null);
    }

    public VirtualSocketAddress(String hub, String machine, int port) 
        throws UnknownHostException {    
        
        this(SocketAddressSet.getByAddress(machine), port, 
                SocketAddressSet.getByAddress(hub), null);
    }
    
    public void write(DataOutput out) throws IOException {

        byte [] m = machine.getAddress();
        
        byte [] h = null;
                
        if (hub != null) { 
            h = hub.getAddress();
        }
        
        byte [] c = null;
        
        if (cluster != null) { 
            c = cluster.getBytes();
        }
        
        out.writeShort(m.length);
        out.writeShort(h == null ? 0 : h.length);               
        out.writeShort(c == null ? 0 : c.length);
        
        out.write(m);
        out.writeInt(port);
        
        if (h != null) { 
            out.write(h);
        }
        
        if (c != null) { 
            out.write(c);
        }        
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
    
    public byte [] toBytes() {
        
        if (codedForm == null) { 
            
             byte [] m = machine.getAddress();             
             byte [] h = hub == null ? new byte[0] : hub.getAddress();             
             byte [] c = cluster == null ? new byte[0] : cluster.getBytes();
             
             int len = 3*2 + 4  + m.length; 

             if (h != null) { 
                 len += h.length;
             }
             
             if (c != null) { 
                 len += c.length;                 
             }
             
             codedForm = new byte[len];
             
             TransferUtils.storeShort((short) m.length, codedForm, 0);
             TransferUtils.storeShort((short) h.length, codedForm, 2);
             TransferUtils.storeShort((short) c.length, codedForm, 4);
             
             System.arraycopy(m, 0, codedForm, 0, m.length);
             
             int off = 6 + m.length;
             
             TransferUtils.storeInt(port, codedForm, off);
             off += 4;
             
             System.arraycopy(h, 0, codedForm, off, h.length);
             off += h.length;
             
             System.arraycopy(c, 0, codedForm, off, c.length);
        }
        
        return codedForm;
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
    
    public static VirtualSocketAddress fromBytes(byte [] source, int offset) 
        throws UnknownHostException {

        int mlen = TransferUtils.readShort(source, offset);
        int hlen = TransferUtils.readShort(source, offset+2);
        int clen = TransferUtils.readShort(source, offset+4);

        int off = offset + 6;
        
        SocketAddressSet machine = SocketAddressSet.fromBytes(source, off);        
        off += mlen;
        
        int port = TransferUtils.readInt(source, offset+6+mlen);
        off += 4;
                
        SocketAddressSet hub = null;
        
        if (hlen > 0) { 
            hub = SocketAddressSet.fromBytes(source, offset+6+mlen+4);
            off += hlen;            
        }
        
        String cluster = null;
        
        if (clen > 0) { 
            cluster = new String(source, offset+6+mlen+4+hlen, clen);
            off += clen;
        }
        
        return new VirtualSocketAddress(machine, port, hub, cluster);
    }
    
    public static VirtualSocketAddress partialAddress(String hostname, 
            int realport, int virtualport) throws UnknownHostException {
     
        return new VirtualSocketAddress(
                SocketAddressSet.getByAddress(hostname, realport), virtualport);
    }
    
    public static VirtualSocketAddress partialAddress(String hostname, 
            int port) throws UnknownHostException {     
        return partialAddress(hostname, port, port);
    }    
    
}
