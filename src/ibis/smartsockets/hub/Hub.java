package ibis.smartsockets.hub;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.discovery.Discovery;
import ibis.smartsockets.hub.connections.HubConnection;
import ibis.smartsockets.hub.connections.MessageForwardingConnectionStatistics;
import ibis.smartsockets.hub.connections.VirtualConnections;
import ibis.smartsockets.hub.state.ConnectionsSelector;
import ibis.smartsockets.hub.state.HubDescription;
import ibis.smartsockets.hub.state.HubList;
import ibis.smartsockets.hub.state.StateCounter;
import ibis.smartsockets.util.NetworkUtils;
import ibis.smartsockets.util.TypedProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hub extends Thread implements StatisticsCallback {

    private static int GOSSIP_SLEEP = 3000;

    private static final int DEFAULT_DISCOVERY_PORT = 24545;
    private static final int DEFAULT_ACCEPT_PORT    = 17878;

    private static final Logger misclogger =
        LoggerFactory.getLogger("ibis.smartsockets.hub.misc");

    private static final Logger goslogger =
        LoggerFactory.getLogger("ibis.smartsockets.hub.gossip");

    private final boolean printStatistics;
    private final long STAT_FREQ;

    private final HubList hubs;
    // private final Map<DirectSocketAddress, BaseConnection> connections;

    private final Connections connections;

    private final Acceptor acceptor;
    private final Connector connector;

    private final StateCounter state = new StateCounter();

    private final Discovery discovery;

    private final VirtualConnections virtualConnections;

    private final String addressFile;

    private long nextStats;

    // FIXME: Quick hack
    private MessageForwardingConnectionStatistics mfcStats =
        new MessageForwardingConnectionStatistics("Connection(*)");

    private boolean done = false;

    public Hub(TypedProperties p) throws IOException {

        super("Hub");

        boolean allowDiscovery =
            p.booleanProperty(SmartSocketsProperties.DISCOVERY_ALLOWED, false);

        String [] clusters =
            p.getStringList(SmartSocketsProperties.HUB_CLUSTERS, ",", null);

        if (clusters == null || clusters.length == 0) {
            clusters = new String[] { "*" };
        }

        boolean allowSSHForHub = p.booleanProperty(
                SmartSocketsProperties.HUB_SSH_ALLOWED, true);

        if (allowSSHForHub) {
            misclogger.info("Hub allowd to use SSH");
            p.setProperty(SmartSocketsProperties.SSH_IN, "true");
            p.setProperty(SmartSocketsProperties.SSH_OUT, "true");
        }

        if (misclogger.isInfoEnabled()) {
            misclogger.info("Creating Hub for clusters: "
                    + Arrays.deepToString(clusters));
        }

        DirectSocketFactory factory = DirectSocketFactory.getSocketFactory(p);

        // Create the hub list
        hubs = new HubList(state);

        connections = new Connections();

        virtualConnections = new VirtualConnections();

        int port = p.getIntProperty(SmartSocketsProperties.HUB_PORT, DEFAULT_ACCEPT_PORT);

        boolean delegate = p.booleanProperty(SmartSocketsProperties.HUB_DELEGATE);

        DirectSocketAddress delegationAddress = null;

        if (delegate) {
            String tmp = p.getProperty(SmartSocketsProperties.HUB_DELEGATE_ADDRESS);

           // System.err.println("**** HUB USING DELEGATION TO: " + tmp);

            misclogger.debug("**** HUB USING DELEGATION TO: " + tmp);

            try {
                delegationAddress = DirectSocketAddress.getByAddress(tmp);
            } catch (Exception e) {
                throw new IOException("Failed to parse delegation address: \""
                        + tmp + "\"");
            }
        }

///        System.err.println("Port = " + port);

        // NOTE: These are not started until later. We first need to init the
        // rest of the world!
        acceptor = new Acceptor(p, port, state, connections, hubs,
                virtualConnections, factory, delegationAddress, this, 5000);

        connector = new Connector(p, state, connections, hubs,
                virtualConnections, factory, this, 5000);

        DirectSocketAddress local = acceptor.getLocal();
        connector.setLocal(local);

        if (goslogger.isInfoEnabled()) {
            goslogger.info("GossipAcceptor listning at " + local);
        }

        String name = p.getProperty(SmartSocketsProperties.HUB_NAME);

        if (name == null || name.length() == 0) {
            // If the simple name is not set, we try to use the hostname
            // instead.
            try {
                name = NetworkUtils.getHostname();
            }  catch (Exception e) {
                if (misclogger.isInfoEnabled()) {
                    misclogger.info("Failed to find simple name for hub!");
                }
            }
        }

        if (misclogger.isInfoEnabled()) {
            misclogger.info("Hub got name: " + name);
        }

        String color = p.getProperty(SmartSocketsProperties.HUB_VIZ_INFO);

        // Create a description for the local machine.
        HubDescription localDesc = new HubDescription(name, local, state, true, color);
        localDesc.setReachable();
        localDesc.setCanReachMe();

        hubs.addLocalDescription(localDesc);

        addHubs(p.getStringList(SmartSocketsProperties.HUB_ADDRESSES));

        if (goslogger.isInfoEnabled()) {
            goslogger.info("Starting Gossip connector/acceptor");
        }

        acceptor.activate();
        connector.activate();

        if (misclogger.isInfoEnabled()) {
            misclogger.info("Listning for broadcast on LAN");
        }

        if (allowDiscovery) {
            String [] suffixes = new String[clusters.length];

            // TODO: what does the + do exactly ???

            // Check if there is a * in the list of clusters. If so, there is no
            // point is passing any other values. Note that there may also be a
            // '+' which means 'any machine -NOT- belonging to a cluster.
            for (int i=0;i<clusters.length;i++) {
                if (clusters[i].equals("*") && clusters.length > 0) {
                    suffixes = new String[] { "*" };
                    break;
                } else if (clusters[i].equals("+")) {
                    suffixes[i] = "+";
                } else {
                    suffixes[i] = " " + clusters[i];
                }
            }

            int dp = p.getIntProperty(SmartSocketsProperties.DISCOVERY_PORT,
                    DEFAULT_DISCOVERY_PORT);

            discovery = new Discovery(dp, 0, 0);
            discovery.answeringMachine("Any Proxies?", suffixes, local.toString());

            misclogger.info("Hub will reply to discovery requests from: " +
                    Arrays.deepToString(suffixes));

        } else {
            discovery = null;
            misclogger.info("Hub will not reply to discovery requests!");
        }

        if (goslogger.isInfoEnabled()) {
            goslogger.info("Start Gossiping!");
        }

        printStatistics = p.booleanProperty(SmartSocketsProperties.HUB_STATISTICS, false);
        STAT_FREQ = p.getIntProperty(SmartSocketsProperties.HUB_STATS_INTERVAL, 60000);

        nextStats = System.currentTimeMillis() + STAT_FREQ;

        addressFile = p.getProperty(SmartSocketsProperties.HUB_ADDRESS_FILE);

        if (addressFile != null && addressFile.length() > 0) {
            writeAddressFile();
        }

        //make this a daemon thread to keep Ibis-Deploy et al from "hanging"
        setDaemon(true);
        start();
    }

    private void writeAddressFile() {

        try {
            File f = new File(addressFile);

            PrintStream out = new PrintStream(new FileOutputStream(f));
            out.println(getHubAddress().toString());
            out.close();

            f.deleteOnExit();
        } catch (Exception e) {
            misclogger.warn("Failed to save address to file!", e);
        }
    }

    public void addHubs(DirectSocketAddress... hubAddresses) {

        DirectSocketAddress local = hubs.getLocalDescription().hubAddress;

        if (hubAddresses != null) {
            for (DirectSocketAddress s : hubAddresses) {
                if (s != null && !local.sameProcess(s)) {
                    misclogger.info("Adding hub address: " + s);
                    hubs.add(s);
                }
            }
        }
    }

    public void addHubs(String... hubAddresses) {

        DirectSocketAddress local = hubs.getLocalDescription().hubAddress;

        if (hubAddresses != null) {
            for (String s : hubAddresses) {

                if (s != null) {
                    try {
                        DirectSocketAddress tmp = DirectSocketAddress.getByAddress(s);

                        if (!local.sameProcess(tmp)) {
                            misclogger.info("Adding hub address: " + s);
                            hubs.add(tmp);
                        }
                    } catch (Exception e) {
                        misclogger.warn("Failed to parse hub address: " + s);
                    }
                }
            }
        }

    }

    private void gossip() {

        if (goslogger.isInfoEnabled()) {
            goslogger.info("Starting gossip round (local state = "
                    + state.get() + ")");
            goslogger.info("I know the following hubs:\n" + hubs.toString());
        }

        ConnectionsSelector selector = new ConnectionsSelector();

        hubs.select(selector);

        for (HubConnection c : selector.getResult()) {
            if (c != null) {
                c.gossip();
            }
        }
    }

    public void delegateAccept(DirectSocket s) {
        acceptor.addIncoming(s);
    }

    public DirectSocketAddress getHubAddress() {
        return acceptor.getLocal();
    }

    public DirectSocketAddress [] knownHubs() {
        return hubs.knownHubs();
    }

    private synchronized boolean getDone() {
        return done;
    }

    public void end() {

        // Shuts down gossip thread.
        synchronized (this) {
            done = true;
        }

        try {
            interrupt();
        } catch (Exception e) {
            // ignore
        }

        // Shut down the other threads....
        acceptor.end();
        connector.end();
    }

    public void add(Statistics s) {

        if (!printStatistics) {
            return;
        }

        if (mfcStats == null) {
            return;
        }

        synchronized (mfcStats) {
            mfcStats.add(s);
        }
    }

    private synchronized void statistics() {

        if (!printStatistics) {
            return;
        }

        if (mfcStats == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now < nextStats) {
            return;
        }

        System.err.println("--- HUB Statistics ---");

        System.err.println(" Connections : " + connections.numberOfConnections());
        System.err.println("  - hubs     : " + connections.numberOfHubs());
        System.err.println("  - clients  : " + connections.numberOfClients());

        System.err.println("--- Connection Statistics ---");

        /*
        DirectSocketAddress [] hubs = connections.hubs();

        for (DirectSocketAddress a : hubs) {

            HubConnection h = connections.getHub(a);

            if (h != null) {
               h.printStatistics();
            }
        }

        DirectSocketAddress [] clients = connections.clients();

        for (DirectSocketAddress a : clients) {

            ClientConnection c = connections.getClient(a);

            if (c != null) {
               c.printStatistics();
            }
        }*/

        synchronized (mfcStats) {
            mfcStats.print(System.err, " ");
        }

        nextStats = now + STAT_FREQ;
    }

    public void run() {

        while (!getDone()) {
            try {
                if (goslogger.isInfoEnabled()) {
                    goslogger.info("Sleeping for " + GOSSIP_SLEEP + " ms.");
                }
                Thread.sleep(GOSSIP_SLEEP);
            } catch (InterruptedException e) {
                // ignore
            }

            gossip();
            statistics();
        }
    }


}
