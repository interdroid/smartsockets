package smartsockets.virtual.modules;

import java.net.UnknownHostException;

import smartsockets.direct.SocketAddressSet;

public abstract class MessagingModule extends ConnectModule {

    protected MessagingModule(String name, boolean requiresServiceLink) {
        super(name, requiresServiceLink);
    }

    protected int toInt(byte [] m) { 
        return (((m[0] & 0xff) << 24) | 
                ((m[1] & 0xff) << 16) |
                ((m[2] & 0xff) << 8) | 
                 (m[3] & 0xff));   
    }
    
    protected byte [] fromInt(int v) {
        return new byte[] { 
                (byte)(0xff & (v >> 24)),
                (byte)(0xff & (v >> 16)),
                (byte)(0xff & (v >> 8)),
                (byte)(0xff & v) };
    }
    
    protected SocketAddressSet toSocketAddressSet(byte [] m) 
        throws UnknownHostException { 
        
        if (m == null || m.length == 0) { 
            return null;
        }
           
        return SocketAddressSet.fromBytes(m);
    }
    
    protected byte [] fromSocketAddressSet(SocketAddressSet s) {
        if (s == null) { 
            return null;
        }
        
        return s.getAddress();
    }
    
 
    protected String toString(byte [] m) {
    
        if (m == null || m.length == 0) { 
            return null;
        }
       
        return new String(m);
    }

    protected byte [] fromString(String s) {
        if (s == null) { 
            return null;
        }
    
        return s.getBytes();        
    }
}