package ibis.connect.gossipproxy;

import java.util.ArrayList;

import ibis.connect.virtual.VirtualSocket;
import ibis.connect.virtual.VirtualSocketAddress;

class ProxyDescription {
        
    // Note: order is important here, we always want the highest possible value!
    static final byte UNKNOWN     = 0;    
    static final byte UNREACHABLE = 1;
    static final byte REACHABLE   = 2;
    
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
           
    void setContactTimeStamp() { 
        lastContact = System.currentTimeMillis();    
    }
    
    private void updateState(int localState) { 
        lastLocalState = localState;
    }

    void setReachable(int localState) { 

        if (reachable != REACHABLE) { 
            reachable = REACHABLE;            
            indirection = null;
            updateState(localState);
        } 
         
        setContactTimeStamp();
    } 
        
    void setUnreachable(int localState) { 

        if (reachable != UNREACHABLE) { 
            reachable = UNREACHABLE;
            updateState(localState);
        } 
        
        setContactTimeStamp();
    } 
        
    void setCanReachMe(int localState, int remoteState) { 

        boolean change = false;
        
        if (canReachMe != REACHABLE) { 
            canReachMe = REACHABLE;    
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
    
    boolean canReachMe() { 
        return canReachMe == REACHABLE;
    }
    
    boolean isReachable() { 
        return reachable == REACHABLE;
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
        
    synchronized ProxyConnection getConnection() { 
        return connection;
    }
    
    private String reachableToString(byte r) { 
        switch (r) { 
        case REACHABLE:
            return "directly";        
        case UNREACHABLE:
            if (indirection != null) { 
                return "indirectly";
            } else {             
                return "no";
            }
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
                
        if (reachable == UNREACHABLE && indirection != null) { 
            buffer.append("Reachable Via: ").append(indirection).append('\n');               
        }        

        buffer.append("Can Reach Me : ").append(reachableToString(canReachMe)).append('\n');
        
        buffer.append("Connection   : ");
                
        if (haveConnection()) {         
            buffer.append("yes\n");
        } else { 
            buffer.append("no\n");
        }
        
        return buffer.toString();        
    }     
}
