package ibis.connect.gossipproxy;

import ibis.connect.direct.SocketAddressSet;
import ibis.connect.gossipproxy.connections.ProxyConnection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;

public class ProxyDescription {
        
    // Note: order is important here, we always want the highest possible value!
    public static final byte UNKNOWN     = 0;    
    public static final byte UNREACHABLE = 1;
    public static final byte REACHABLE   = 2;
    
    public final SocketAddressSet proxyAddress;
    public final String proxyAddressAsString;
    
    final StateCounter state;        
    final boolean local;
    
    private ProxyDescription indirection;     
        
    // Value of the local state the last time anything was changed in this 
    // description.  
    private long lastLocalUpdate;
    
    // Value of the remote state the last time anything was changed in original
    // copy of this description.  
    private long homeState;
        
    // Number of hops required to reach this machine. A value of '0' indicates 
    // that a direct connection is possible. A value of 'Integer.MAX_VALUE/2'
    // or large indicates an unreachable machine. NOTE: The '/2' is used to 
    // prevent overflow when we forward this information to other machines. Each 
    // forward adds '1' to the hop count, so 'Integer.MAX_VALUE' would not work. 
    private int hops = Integer.MAX_VALUE/2; 
        
    // Last time that there was any contact with this machine.  
    private long lastContact;    
    
    // Last time that we tried to connect to this machine.      
    private long lastConnect;    
        
    private byte reachable  = UNKNOWN;
    private byte canReachMe = UNKNOWN;

    // Maintain a list of machines that have registered themselves as clients. 
    // Note that this is probably a very bad idea from a scalabitly point of 
    // view...
    private TreeMap clients = new TreeMap(); 

    private ProxyConnection connection;
  
    ProxyDescription(SocketAddressSet address, StateCounter state) {
        this(address, state, false);
    } 
    
    ProxyDescription(SocketAddressSet address, StateCounter state, 
            boolean local) {
        
        this.state = state;        
        this.proxyAddress = address;
        this.proxyAddressAsString = address.toString();
        this.lastLocalUpdate = state.increment();
        
        this.reachable = UNKNOWN;
        this.canReachMe = UNKNOWN;
        
        this.local = local;
    }
    
    public boolean addClient(String client) {
        
        if (!local) { 
            throw new IllegalStateException("Cannot add clients to remote"
                    + " proxy descriptions!");
        }
        
        synchronized (clients) {            
            if (clients.containsKey(client)) {
                return false;
            } 

            lastLocalUpdate = state.increment();
            clients.put(client, new ClientDescription(client));
            return true;
        } 
    }

    public boolean removeClient(String client) {
        
        if (!local) { 
            throw new IllegalStateException("Cannot remove clients from remote"
                    + " proxy descriptions!");
        }
                
        synchronized (clients) {            
            if (!clients.containsKey(client)) {
                return false;
            } 
            
            lastLocalUpdate = state.increment();
            clients.remove(client);
            return true;
        } 
    }
    
    public void update(ClientDescription [] c, long remoteState) {
        
        if (local) { 
            throw new IllegalStateException("Cannot update clients of local"
                    + " proxy description!");
        }
                
        synchronized (clients) {            
            clients.clear();

            for (int i=0;i<c.length;i++) {
                clients.put(c[i].clientAddress, c[i]);                                
            }   
        }            
        
        homeState = remoteState;
        lastLocalUpdate = state.get();
    }
   
    public long getHomeState() { 
        return homeState;
    }
    
    public boolean addService(String client, String tag, String address) {
        synchronized (clients) {
            
            if (!clients.containsKey(client)) {
                return false;
            } 

            ClientDescription c = (ClientDescription) clients.get(client);
            return c.addService(tag, address);
        }
    }
    
    boolean containsClient(String client) {
        synchronized (client) {
            return clients.containsKey(client);
        } 
    }
    
    public ArrayList getClients(String tag) {
        
        ArrayList result = new ArrayList();
        
        synchronized (clients) {           
            Iterator itt = clients.values().iterator();
            
            while (itt.hasNext()) {
                
                ClientDescription c = (ClientDescription) itt.next();
                
                if (c.containsService(tag)) { 
                    result.add(c);
                } 
            }
        }
        
        return result;
    }
    
    
    public synchronized void setContactTimeStamp(boolean connect) { 
        lastContact = System.currentTimeMillis();
        
        if (connect) { 
            lastConnect = lastContact;            
        }
    }
    
    public synchronized long getLastLocalUpdate() { 
        return lastLocalUpdate;
    }    
    
    synchronized long getLastConnect() {
        return lastConnect;
    }
    
    synchronized long getLastContact() {
        return lastContact;
    }
    
    public synchronized int getHops() { 
        return hops;
    }
    
    synchronized void setReachable() {

        if (reachable != REACHABLE) { 
            reachable = REACHABLE;                     
            indirection = null;
            hops = 0;
            lastLocalUpdate = state.increment();
        } 
         
        setContactTimeStamp(true);
    } 
        
    synchronized void setUnreachable() { 
        
        if (reachable != UNREACHABLE) { 
            reachable = UNREACHABLE;
            lastLocalUpdate = state.increment();
        } 

        setContactTimeStamp(true);        
    } 
        
    public synchronized void setCanReachMe() { 
        
        if (canReachMe != REACHABLE) { 
            canReachMe = REACHABLE;                      
            lastLocalUpdate = state.increment();
            
            hops = 0;
            indirection = null;            
        }        
        
        setContactTimeStamp(false);        
    }
    
    public synchronized void setCanNotReachMe() {

        if (canReachMe != UNREACHABLE) { 
            canReachMe = UNREACHABLE;                      
            lastLocalUpdate = state.increment();
        }        
        
        setContactTimeStamp(false);
    }
    
    public synchronized void addIndirection(ProxyDescription indirection, int hops) {
        
        if (reachable != REACHABLE && hops < this.hops) {
            this.hops = hops;
            this.indirection = indirection;
            lastLocalUpdate = state.increment();
        } 
    }
    
    public synchronized ProxyDescription getIndirection() {
        return indirection;
    }
        
 //   boolean isStable() {         
 //       return reachableKnown() && canReachMeKnown();        
 //   }
  
    public synchronized boolean directlyReachable() {
        return (reachable == REACHABLE || canReachMe == REACHABLE);               
    }
    
    public synchronized boolean reachableKnown() {         
        return (reachable != UNKNOWN);        
    }
    
//    boolean canReachMeKnown() {         
//        return (canReachMe != UNKNOWN);        
//    }
        
    public synchronized boolean canReachMe() { 
        return canReachMe == REACHABLE;
    }
    
    public synchronized boolean canReachMeKnown() {         
        return (canReachMe != UNKNOWN);        
    }
        
    synchronized boolean isReachable() { 
        return reachable == REACHABLE;
    }
        
    public synchronized boolean isLocal() { 
        return local;
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

    synchronized boolean haveConnection() { 
        return (connection != null);        
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
        
        if (local) {                 
            buffer.append("Last Update  : ").append(lastLocalUpdate).append('\n');
        } else { 
            buffer.append("Home State   : ").append(homeState).append('\n');
                
            long time = (System.currentTimeMillis() - lastContact) / 1000;
        
            buffer.append("Last Contact : ").append(time).append(" seconds ago\n");
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
        } 
        
        buffer.append("Clients      : ");
        buffer.append(clients.size());
        buffer.append("\n");
                
        Iterator itt = clients.values().iterator();
        
        while (itt.hasNext()) { 
            buffer.append("             : ");
            buffer.append((ClientDescription) itt.next());
            buffer.append("\n");                
        } 
        
        return buffer.toString();        
    }
         
}
