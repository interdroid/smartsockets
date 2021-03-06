package ibis.smartsockets.hub.state;


import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.hub.connections.HubConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;


public class HubDescription {

    // Note: order is important here, we always want the highest possible value!
    public static final byte UNKNOWN     = 0;
    public static final byte UNREACHABLE = 1;
    public static final byte REACHABLE   = 2;

    // Address of the hub, which should be unique. Also stored in String form,
    // since this is used quite a lot...
    public final DirectSocketAddress hubAddress;
    public final String hubAddressAsString;

    // The human-readable name of the hub. Note that this is not used for
    // anything other that printing information for the user, since the name is
    // not guaranteed to be set or unique.
    private String name;

    // Easy access to the global state counter
    final StateCounter state;

    private String vizInfo;

    // Is this the local description ?
    final boolean local;

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

    // Is the machine reachable ? Can it reach me ?
    private byte reachable  = UNKNOWN;
    private byte canReachMe = UNKNOWN;

    // Maintain a list of machines that have registered themselves as clients.
    // Note that this is only used on the local hub.
    private HashMap<DirectSocketAddress, ClientDescription> clients;

    // For remote machines we only maintain a client count.
   // private int numberOfClients;

    // A reference to the actual connection to the described hub. May be
    // null if we are not directly connected.
    private HubConnection connection;

    // If we don't have a connection to a hub, this reference tells us who
    // informed us in the first place that this hub exist. This allows us to
    // indirectly reach the hub if necessary.
    private HubDescription indirection;

    // List of other hubs that this hub is connected to. Can be used by a client
    // (e.g., a visualization) to get idea of who's connected to whom. Stored in
    // String form since it's not really worth the effort converting them all
    // the time.
    private ArrayList<String> connectedTo = new ArrayList<String>();

    public HubDescription(DirectSocketAddress address, StateCounter state) {
        this(null, address, state, false, null);
    }

    public HubDescription(String name, DirectSocketAddress address,
            StateCounter state, boolean local, String vizInfo) {

        this.name = name;
        this.state = state;
        this.hubAddress = address;
        this.hubAddressAsString = address.toString();
        this.lastLocalUpdate = state.increment();

        if (vizInfo == null) {
            this.vizInfo = "";
        } else {
            this.vizInfo = vizInfo;
        }

        this.reachable = UNKNOWN;
        this.canReachMe = UNKNOWN;

        this.local = local;
        this.clients = new HashMap<DirectSocketAddress, ClientDescription>();
    }

    public boolean addClient(DirectSocketAddress client) {

        if (!local) {
            throw new IllegalStateException("Cannot add clients to remote"
                    + " hub descriptions!");
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

    public boolean knowsClient(DirectSocketAddress client) {

        synchronized (clients) {
            return clients.containsKey(client);
        }
    }

    public boolean removeClient(DirectSocketAddress client) {

        if (!local) {
            throw new IllegalStateException("Cannot remove clients from remote"
                    + " hub descriptions!");
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

    public void update(ClientDescription [] clients, String [] connectedTo,
            String name, long remoteState, String vizInfo) {

        if (local) {
            throw new IllegalStateException("Cannot update the local"
                    + " hub description!");
        }

        synchronized (this.clients) {
            this.clients.clear();

            for (ClientDescription c : clients) {
                this.clients.put(c.clientAddress, c);
            }
        }

        synchronized (this.connectedTo) {
            this.connectedTo.clear();

            for (String s : connectedTo) {
                this.connectedTo.add(s);
            }
        }

        if (name.length() > 0) {
            synchronized (this) {
                if (this.name == null || this.name.length() == 0) {
                    this.name = name;
                }
            }
        }

        homeState = remoteState;
        lastLocalUpdate = state.increment();
        this.vizInfo = vizInfo;
    }

    public long getHomeState() {
        return homeState;
    }

    public boolean addService(DirectSocketAddress client, String tag,
            String address) {

        synchronized (clients) {

            if (!clients.containsKey(client)) {
                return false;
            }

            ClientDescription c = clients.get(client);

            if (c.addService(tag, address)) {
                lastLocalUpdate = state.increment();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean updateService(DirectSocketAddress client, String tag,
            String address) {

        synchronized (clients) {

            if (!clients.containsKey(client)) {
                return false;
            }

            ClientDescription c = clients.get(client);

            if (c.updateService(tag, address)) {
                lastLocalUpdate = state.increment();
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean removeService(DirectSocketAddress client, String tag) {
        synchronized (clients) {

            if (!clients.containsKey(client)) {
                return false;
            }

            ClientDescription c = clients.get(client);

            if (c.removeService(tag)) {
                lastLocalUpdate = state.increment();
                return true;
            } else {
                return false;
            }
        }
    }

    boolean containsClient(DirectSocketAddress client) {
        synchronized (client) {
            return clients.containsKey(client);
        }
    }

    public int numberOfClients() {
        return clients.size();
    }

    public ArrayList<ClientDescription> getClients(String tag) {

        ArrayList<ClientDescription> result =
            new ArrayList<ClientDescription>();

        synchronized (clients) {

            for (ClientDescription c : clients.values()) {
                if (c.containsService(tag)) {
                    result.add(c);
                }
            }
        }

        return result;
    }

    public void getClientsAsString(LinkedList<String> result, String tag) {

        if (clients == null) {
            // TODO: send message to client to get this info ?
            return;
        }

        synchronized (clients) {
            for (ClientDescription c : clients.values()) {
                if (c.containsService(tag)) {
                    result.add(c.toString());
                }
            }
        }
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

    public synchronized void setReachable() {

        if (reachable != REACHABLE) {
            reachable = REACHABLE;
            indirection = null;
            hops = 0;
            lastLocalUpdate = state.increment();
        }

        setContactTimeStamp(true);
    }

    public synchronized void setUnreachable() {

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

    public synchronized boolean addIndirection(HubDescription indirection,
            int hops) {

        if (reachable != REACHABLE && hops < this.hops) {
            this.hops = hops;
            this.indirection = indirection;
            lastLocalUpdate = state.increment();

            return true;
        }

        return false;
    }

    public synchronized HubDescription getIndirection() {
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

    public synchronized boolean isReachable() {
        return reachable == REACHABLE;
    }

    public synchronized boolean isLocal() {
        return local;
    }

    public synchronized boolean createConnection(HubConnection c) {

        if (connection != null) {
            // Already have a connection to this hub!
            return false;
        }

        connection = c;
        return true;
    }

    public synchronized HubConnection getConnection() {
        return connection;
    }

    public String [] connectedTo() {
        synchronized (connectedTo) {
            return connectedTo.toArray(new String [connectedTo.size()]);
        }
    }

    public synchronized boolean haveConnection() {
        return (connection != null);
    }

    public synchronized void removeConnection() {
        connection = null;
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

        String n = getName();

        if (n.length() > 0) {
            buffer.append("Name         : ").append(name).append('\n');
        } else {
            buffer.append("Name         : <unknown>").append('\n');
        }

        buffer.append("Address      : ").append(hubAddress).append('\n');
        buffer.append("Last Update  : ").append(lastLocalUpdate).append('\n');

        if (!local) {
            buffer.append("Home State   : ").append(homeState).append('\n');

            long time = (System.currentTimeMillis() - lastContact) / 1000;

            buffer.append("Last Contact : ").append(time).append(" seconds ago\n");
            buffer.append("Reachable    : ").append(reachableToString(reachable)).append('\n');

            if (reachable == UNREACHABLE && indirection != null) {
                buffer.append("Reachable Via: ").append(
                        indirection.hubAddressAsString).append('\n');
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

        synchronized (connectedTo) {
            buffer.append("Connections  : ");
            buffer.append(connectedTo.size());
            buffer.append("\n");

            for (String s : connectedTo) {
                buffer.append("             : ");
                buffer.append(s);
                buffer.append("\n");
            }
        }

        synchronized (clients) {
            buffer.append("Clients      : ");
            buffer.append(clients.size());
            buffer.append("\n");

            for (ClientDescription c : clients.values()) {
                buffer.append("             : ");
                buffer.append(c.toString());
                buffer.append("\n");
            }
        }
        return buffer.toString();
    }

    public void addConnectedTo(String address) {

        if (!local) {
            throw new IllegalStateException("Cannot add connections to remote"
                    + " hub descriptions!");
        }

        synchronized (connectedTo) {
            connectedTo.add(address);
            lastLocalUpdate = state.increment();
        }
    }

    public void removeConnectedTo(String address) {

        if (!local) {
            throw new IllegalStateException("Cannot remove connections from" +
                    " remote hub descriptions!");
        }

        synchronized (connectedTo) {

            lastLocalUpdate = state.increment();

            for (int i=0;i<connectedTo.size();i++) {
                if (connectedTo.get(i).startsWith(address)) {
                    connectedTo.remove(i);
                    return;
                }
            }
        }
    }

    public synchronized void setName(String name) {
        this.name = name;
    }

    public synchronized String getName() {

        if (name == null) {
            return "";
        }

        return name;
    }

    public String getVizInfo() {
        return vizInfo;
    }
}
