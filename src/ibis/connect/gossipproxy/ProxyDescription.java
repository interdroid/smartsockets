package ibis.connect.gossipproxy;

import java.util.ArrayList;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;

class ProxyDescription {
        
    // Note: order is important here, we always want the highest possible value!
    static final byte UNKNOWN     = 0;    
    static final byte UNREACHABLE = 1;
    static final byte INDIRECT    = 2;    
    static final byte DIRECT      = 3;
    
    final VirtualSocketAddress proxyAddress;    
    VirtualSocketAddress indirection; 
    
    int lastKnownState;
    int lastLocalState;
    
    long lastContact;    
           
    byte reachable  = UNKNOWN;
    byte canReachMe = UNKNOWN;

    ArrayList clients = new ArrayList(); 

    private ProxyConnection connection;
    
    ProxyDescription(VirtualSocketAddress address, 
            VirtualSocketAddress indirection, int localState, int remoteState) {
        
        this.proxyAddress = address;
        this.indirection = indirection;        
        this.lastKnownState = remoteState;   
        this.lastLocalState = localState;
                
        this.reachable = UNKNOWN;
        this.canReachMe = UNKNOWN;                
    }
    
    void addClient(VirtualSocketAddress client) { 
        clients.add(client);
    }
        
    void setContactTimeStamp(long value) {     
        if (value > lastContact) { 
            lastContact = value;
        }            
    }
        
    private void setContactTimeStamp() { 
        lastContact = System.currentTimeMillis();    
    }
    
    private void updateState(int localState) { 
        lastLocalState = localState;
    }

    void setReachable(int localState, int remoteState, byte reachMe) { 

        boolean change = false;
        
        if (reachable != DIRECT) { 
            reachable = DIRECT;            
            indirection = null;
            change = true;
        }
        
        if (lastKnownState < remoteState) {
            lastKnownState = remoteState;
            change = true;
        }

        if (canReachMe < reachMe) { 
            canReachMe = reachMe;
            change = true;
        }
        
        if (change) { 
            updateState(localState);
        } 
         
        setContactTimeStamp();
    } 
        
    void setUnreachable(int localState) { 
               
        if (reachable == UNKNOWN || reachable == DIRECT) {
            
            if (indirection != null) { 
                reachable = INDIRECT;
            } else { 
                reachable = UNREACHABLE;
            }

            updateState(localState);
        }
         
        setContactTimeStamp();
    } 
        
    void setCanReachMe(int localState, int remoteState) { 

        boolean change = false;
        
        if (canReachMe != DIRECT) { 
            canReachMe = DIRECT;    
            change = true;
        }        
        
        if (lastKnownState < remoteState) {
            lastKnownState = remoteState;
            change = true;
        }

        if (change) { 
            updateState(localState);
        } 
        
        setContactTimeStamp();
    }
    
    void setCanNotReachMe() { 
        canReachMe = UNREACHABLE;
        setContactTimeStamp();
    }
    
    boolean canDirectlyReachMe() { 
        return canReachMe == DIRECT;
    }
    
    boolean isDirectlyReachable() { 
        return reachable == DIRECT;
    }
    
    boolean isIndirectlyReachable() { 
        return reachable == INDIRECT;
    }

    boolean haveConnection() { 
        return (connection != null);        
    }
    
    synchronized boolean createConnection(ProxyConnection c) {
        
        if (connection != null) {
            // Already have a connection to this proxy!
            return false;
        }
        
        connection = c;
        return true;
    }
        
    private String reachableToString(byte r) { 
        switch (r) { 
        case DIRECT:
            return "directly";
        case INDIRECT:
            return "indirectly";
        case UNREACHABLE:
            return "unreachable";
        default:
            return "unknown";
        }
    }
    
    public String toString() { 
    
        StringBuffer buffer = new StringBuffer();
        buffer.append("Address      : ").append(proxyAddress).append('\n');                      
        buffer.append("Last State   : ").append(lastKnownState).append('\n');
        
        long time = (System.currentTimeMillis() - lastContact) / 1000;
        
        buffer.append("Last Update  : ").append(time).append(" seconds ago\n");
        buffer.append("Reachable    : ").append(reachableToString(reachable)).append('\n');
                
        if (reachable != DIRECT && indirection != null) { 
            buffer.append("Reachable Via: ").append(indirection).append('\n');               
        }        

        buffer.append("Can Reach Me : ").append(reachableToString(canReachMe)).append('\n');
        
        return buffer.toString();        
    }     
}
