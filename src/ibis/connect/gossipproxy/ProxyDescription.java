package ibis.connect.gossipproxy;

import ibis.connect.virtual.VirtualSocketAddress;

import java.util.ArrayList;

class ProxyDescription {
        
    // Note: order is important here, we always want the highest possible value!
    static final byte UNKNOWN     = 0;    
    static final byte UNREACHABLE = 1;
    static final byte REACHABLE   = 2;
    
    final VirtualSocketAddress proxyAddress;    
    VirtualSocketAddress indirection; 
    
    final StateCounter state;
    
    // Value of the local state the last time anything was changed in this 
    // description.  
    private long lastLocalUpdate;
    
    // Indicates the value of the local state the last time any data was send 
    // to this machine. This allows us to send delta's instead of all info. 
    private long lastSendState;
        
    // Number of hops required to reach this machine. A value of '0' indicates 
    // that a direct connection is possible. A value of 'Integer.MAX_VALUE/2'
    // or large indicates an unreachable machine. NOTE: The '/2' is used to 
    // prevent overflow when we forward this information to other machines. Each 
    // forward adds '1' to the hop count, so 'Integer.MAX_VALUE' would not work. 
    private int hops = Integer.MAX_VALUE/2; 
        
    // Last time that there was any contact with this machine.  
    long lastContact;    
    
    // Last time that we tried to connect to this machine.      
    long lastConnect;    
        
    byte reachable  = UNKNOWN;
    byte canReachMe = UNKNOWN;

    // Maintain a list of machines that have registered themselves as clients. 
    // Note that this is probably a very bad idea from a scalabitly point of 
    // view...
    ArrayList clients = new ArrayList(); 

    private ProxyConnection connection;
    
    ProxyDescription(VirtualSocketAddress address, StateCounter state) {
        
        this.state = state;        
        this.proxyAddress = address;
        this.lastLocalUpdate = state.increment();
        
        this.reachable = UNKNOWN;
        this.canReachMe = UNKNOWN;                
    }
    
  //  void addClient(VirtualSocketAddress client) { 
  //      clients.add(client);
  //  }

    void addClient(String clientAsString) {        
        // TODO: optimize!!!
        if (!clients.contains(clientAsString)) {   
            this.lastLocalUpdate = state.increment();            
            clients.add(clientAsString);
        } 
    }
    
    void setContactTimeStamp(boolean connect) { 
        lastContact = System.currentTimeMillis();
        
        if (connect) { 
            lastConnect = lastContact;            
        }
    }
    
    public long getLastLocalUpdate() { 
        return lastLocalUpdate;
    }
    
    public long getLastSendState() { 
        return lastSendState;
    }

    public void setLastSendState() {
        lastSendState = state.get();
    }
    
    public int getHops() { 
        return hops;
    }
    
    void setReachable() {

        if (reachable != REACHABLE) { 
            reachable = REACHABLE;                     
            indirection = null;
            hops = 0;
            lastLocalUpdate = state.increment();
        } 
         
        setContactTimeStamp(true);
    } 
        
    void setUnreachable() { 
        
        if (reachable != UNREACHABLE) { 
            reachable = UNREACHABLE;
            lastLocalUpdate = state.increment();
        } 

        setContactTimeStamp(true);        
    } 
        
    void setCanReachMe() { 
        
        if (canReachMe != REACHABLE) { 
            canReachMe = REACHABLE;                      
            lastLocalUpdate = state.increment();
        }        
        
        setContactTimeStamp(false);        
    }
    
    void setCanNotReachMe() {

        if (canReachMe != UNREACHABLE) { 
            canReachMe = UNREACHABLE;                      
            lastLocalUpdate = state.increment();
        }        
        
        setContactTimeStamp(false);
    }
    
    void addIndirection(VirtualSocketAddress indirection, int hops) {
        
        if (reachable != REACHABLE && hops < this.hops) {
            this.hops = hops;
            this.indirection = indirection;
            lastLocalUpdate = state.increment();
        } 
    }
    
    boolean isStable() {         
        return reachableKnown() && canReachMeKnown();        
    }
    
    boolean reachableKnown() {         
        return (reachable != UNKNOWN);        
    }
    
    boolean canReachMeKnown() {         
        return (canReachMe != UNKNOWN);        
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
        buffer.append("Last Update  : ").append(lastLocalUpdate).append('\n');
        buffer.append("Last Gossip  : ").append(lastSendState).append('\n');
                
        long time = (System.currentTimeMillis() - lastContact) / 1000;
        
        buffer.append("Last Update  : ").append(time).append(" seconds ago\n");
        buffer.append("Reachable    : ").append(reachableToString(reachable)).append('\n');
                
        if (reachable == UNREACHABLE && indirection != null) { 
            buffer.append("Reachable Via: ").append(indirection).append('\n');
        }        

        buffer.append("Required Hops: ").append(hops).append('\n');
        
        buffer.append("Can Reach Me : ").append(reachableToString(canReachMe)).append('\n');
        
        buffer.append("Connection   : ");
                
        if (haveConnection()) {         
            buffer.append("yes\n");
        } else { 
            buffer.append("no\n");
        }
        
        buffer.append("Clients      : ");
        buffer.append(clients.size());
        buffer.append("\n");
                
        for (int i=0;i<clients.size();i++) { 
            buffer.append("             : ");
            buffer.append(clients.get(i));
            buffer.append("\n");                
        } 
        
        return buffer.toString();        
    }     
}
