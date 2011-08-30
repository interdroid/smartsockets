package ibis.smartsockets.virtual;

import ibis.smartsockets.SmartSocketsProperties;
import ibis.smartsockets.direct.DirectSocket;
import ibis.smartsockets.direct.DirectSocketAddress;
import ibis.smartsockets.direct.DirectSocketFactory;
import ibis.smartsockets.discovery.Discovery;
import ibis.smartsockets.hub.Hub;
import ibis.smartsockets.hub.servicelink.ServiceLink;
import ibis.smartsockets.util.TypedProperties;
import ibis.smartsockets.virtual.modules.AbstractDirectModule;
import ibis.smartsockets.virtual.modules.AcceptHandler;
import ibis.smartsockets.virtual.modules.ConnectModule;
import ibis.smartsockets.virtual.modules.direct.Direct;
import ibis.smartsockets.util.ThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements a virtual socket factory.
 *
 * The VirtualSocketFactory is the public interface to the virtual connection
 * layer of SmartSockets. It implements several types of connection setup using
 * the direct connection layer (see {@link DirectSocketFactory}) and a
 * {@link ServiceLink} to the {@link Hub}.
 *<p>
 * The different connection setup schemes are implemented in separate modules
 * each extending the {@link ConnectModule} class.
 *<p>
 * Currently, 4 different connect modules are available:<br>
 *<p>
 * {@link Direct}: creates a direct connection to the target.<br>
 * {@link ibis.smartsockets.virtual.modules.reverse.Reverse Reverse}: reverses the connection setup.such that the target creates a
 * direct connection to the source.<br>
 * {@link ibis.smartsockets.virtual.modules.splice.Splice Splice}: uses TCP splicing to create a direct connection to the
 * target.<br>
 * {@link ibis.smartsockets.virtual.modules.hubrouted.Hubrouted Hubrouted}: create a virtual connection that routes all traffic
 * through the hub overlay.<br>
 *<p>
 * To create a new connection, each module is tried in sequence until a
 * connection is established, or until it is clear that a connection can not be
 * established at all (e.g., because the destination port does not exist on the
 * target machine). By default, the order<br>
 *<p>
 * Direct, Reverse, Splice, Routed
 * <p>is used. This order prefers modules that produce a direct connection. This
 * default order can be changed using the
 * smartsockets.modules.order property. The order can also be
 * adjusted on a per connection basis by providing this property to the
 * createClientSocket method.
 *
 * @see ibis.smartsockets.virtual.modules
 * @see DirectSocketFactory
 * @see ServiceLink
 * @see Hub
 *
 * @author Jason Maassen
 * @version 1.0 Jan 30, 2006
 * @since 1.0
 *
 */
public final class VirtualSocketFactory {

    /**
     * An inner class the prints connection statistics at a regular interval.
     */
    private static class StatisticsPrinter implements Runnable {

        private int timeout;
        private VirtualSocketFactory factory;
        private String prefix;

        StatisticsPrinter(final VirtualSocketFactory factory, final int timeout,
                final String prefix) {
            this.timeout = timeout;
            this.factory = factory;
            this.prefix = prefix;
        }

        public void run() {

            int t = getTimeout();

            while (t > 0) {
                try {
                    synchronized (this) {
                        wait(t);
                    }
                } catch (InterruptedException e) {
                    // ignore
                }

                t = getTimeout();

                if (t > 0) {
                    try {
                        factory.printStatistics(prefix);
                    } catch (Exception e) {
                        logger.warn("Failed to print statistics", e);
                    }
                }
            }
        }

        private synchronized int getTimeout() {
            return timeout;
        }

        public synchronized void adjustInterval(final int interval) {
            timeout = interval;
            notifyAll();
        }
    }

    private static final Map<String, VirtualSocketFactory> factories =
            new HashMap<String, VirtualSocketFactory>();

    private static VirtualSocketFactory defaultFactory = null;

    /** Generic logger for this class. */
    protected static final Logger logger =
            LoggerFactory.getLogger("ibis.smartsockets.virtual.misc");

    /** Logger for connection related logging. */
    protected static final Logger conlogger =
            LoggerFactory.getLogger("ibis.smartsockets.virtual.connect");

    private static final Logger statslogger = LoggerFactory
            .getLogger("ibis.smartsockets.statistics");

    private final DirectSocketFactory directSocketFactory;

    private final ArrayList<ConnectModule> modules =
            new ArrayList<ConnectModule>();

    private ConnectModule direct;

    private final TypedProperties properties;

    private final int DEFAULT_BACKLOG;

    private final int DEFAULT_TIMEOUT;

    private final int DEFAULT_ACCEPT_TIMEOUT;

    private final boolean DETAILED_EXCEPTIONS;

    private final Random random;

    private final HashMap<Integer, VirtualServerSocket> serverSockets =
            new HashMap<Integer, VirtualServerSocket>();

    private int nextPort = 3000;

    private DirectSocketAddress myAddresses;

    private DirectSocketAddress hubAddress;

    private VirtualSocketAddress localVirtualAddress;

    private String localVirtualAddressAsString;

    private ServiceLink serviceLink;

    private Hub hub;

    private VirtualClusters clusters;

    private boolean printStatistics = false;

    private String statisticPrefix = null;

    private StatisticsPrinter printer = null;

    /**
     * An inner class that accepts incoming connections for a hub.
     *
     * Only used when hub delegation is active: a hub and VirtualSocketFactory
     * are running in the same process and share one contact address. This is
     * only used in special server processes (such as the Ibis Registry).
     */
    private static class HubAcceptor implements AcceptHandler {

        private final Hub hub;

        private HubAcceptor(Hub hub) {
            this.hub = hub;
        }

        public void accept(DirectSocket s, int targetPort, long time) {
            hub.delegateAccept(s);
        }
    }

    private VirtualSocketFactory(DirectSocketFactory df, TypedProperties p)
            throws InitializationException {

        directSocketFactory = df;

        if (logger.isInfoEnabled()) {
            logger.info("Creating VirtualSocketFactory");
        }

        random = new Random();

        properties = p;

        DETAILED_EXCEPTIONS = p.booleanProperty(
                SmartSocketsProperties.DETAILED_EXCEPTIONS, false);

        DEFAULT_BACKLOG = p.getIntProperty(SmartSocketsProperties.BACKLOG, 50);

        DEFAULT_ACCEPT_TIMEOUT = p.getIntProperty(
                SmartSocketsProperties.ACCEPT_TIMEOUT, 60000);

        // NOTE: order is VERY important here!
        try {
            loadModules();
        } catch (Exception e) {
            logger.info("Failed to load modules!", e);
            throw new InitializationException(e.getMessage(), e);
        }

        if (modules.size() == 0) {
            logger.info("Failed to load any modules!");
            throw new InitializationException("Failed to load any modules!");
        }

        // -- this depends on the modules being loaded
        DEFAULT_TIMEOUT = determineDefaultTimeout(p);

        startHub(p);

        // We now create the service link. This may connect to the hub that we
        // have just started.
        String localCluster = p.getProperty(
                SmartSocketsProperties.CLUSTER_MEMBER, null);

        createServiceLink(localCluster);

        // Once the servicelink is up and running, we can start the modules.
        startModules();

        if (modules.size() == 0) {
            logger.info("Failed to start any modules!");
            throw new InitializationException("Failed to load any modules!");
        }

        loadClusterDefinitions();

        localVirtualAddress = new VirtualSocketAddress(myAddresses, 0,
                hubAddress, clusters.localCluster());

        localVirtualAddressAsString = localVirtualAddress.toString();

        printStatistics =
                p.booleanProperty(SmartSocketsProperties.STATISTICS_PRINT);

        if (printStatistics) {
            statisticPrefix = p.getProperty(
                    SmartSocketsProperties.STATISTICS_PREFIX, "SmartSockets");

            int tmp = p.getIntProperty(
                    SmartSocketsProperties.STATISTICS_INTERVAL, 0);

            if (tmp > 0) {
                printer = new StatisticsPrinter(this, tmp * 1000,
                        statisticPrefix);
                ThreadPool
                        .createNew(printer, "SmartSockets Statistics Printer");
            }
        }
    }

    private void startHub(TypedProperties p)
            throws InitializationException {

        if (p.booleanProperty(SmartSocketsProperties.START_HUB, false)) {

            AbstractDirectModule d = null;

            // Check if the hub should delegate it's accept call to the direct
            // module. This way, only a single server port (and address) is
            // needed to reach both this virtual socket factory and the hub.
            boolean delegate = p.booleanProperty(
                    SmartSocketsProperties.HUB_DELEGATE, false);

            if (delegate) {
                logger.info("Factory delegating hub accepts to direct module!");

                // We should now add an AcceptHandler to the direct module that
                // intercepts incoming connections for the hub. Start by finding
                // the direct module...
                for (ConnectModule m : modules) {
                    if (m.module.equals("ConnectModule(Direct)")) {
                        d = (AbstractDirectModule) m;
                        break;
                    }
                }

                if (d == null) {
                    throw new InitializationException("Cannot start hub: "
                            + "Failed to find direct module!");
                }

                // And add its address to the property set as the 'delegation'
                // address. This is needed by the hub (since it needs to know
                // its own address).
                p.setProperty(SmartSocketsProperties.HUB_DELEGATE_ADDRESS, d
                        .getAddresses().toString());
            }

            // Now we create the hub
            logger.info("Factory is starting hub");

            try {
                hub = new Hub(p);
                logger.info("Hub running on: " + hub.getHubAddress());
            } catch (IOException e) {
                throw new InitializationException("Failed to start hub", e);
            }

            // Finally, if delegation is used, we install the accept handler
            if (delegate) {

                // Get the 'virtual port' that the hub pretends to be on.
                int port = p.getIntProperty(
                        SmartSocketsProperties.HUB_VIRTUAL_PORT, 42);

                d.installAcceptHandler(port, new HubAcceptor(hub));
            }
        }
    }

    private void loadClusterDefinitions() {
        clusters = new VirtualClusters(this, properties, getModules());
    }

    private DirectSocketAddress discoverHub(String localCluster) {

        DirectSocketAddress address = null;

        if (logger.isInfoEnabled()) {
            logger.info("Attempting to discover hub using UDP multicast...");
        }

        int port = properties
                .getIntProperty(SmartSocketsProperties.DISCOVERY_PORT);

        int time = properties
                .getIntProperty(SmartSocketsProperties.DISCOVERY_TIMEOUT);

        Discovery d = new Discovery(port, 0, time);

        String message = "Any Proxies? ";

        message += localCluster;

        String result = d.broadcastWithReply(message);

        if (result != null) {
            try {
                address = DirectSocketAddress.getByAddress(result);
                if (logger.isInfoEnabled()) {
                    logger.info("Hub found at: " + address.toString());
                }
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Got unknown reply to hub discovery!");
                }
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("No hubs found.");
            }
        }

        return address;
    }

    private void createServiceLink(String localCluster) {

        List<DirectSocketAddress> hubs = new LinkedList<DirectSocketAddress>();

        if (hub != null) {
            hubs.add(hub.getHubAddress());
        }

        // Check if the hub address was passed as a property.
        String[] tmp = properties
                .getStringList(SmartSocketsProperties.HUB_ADDRESSES);

        if (tmp != null && tmp.length > 0) {
            for (String a : tmp) {
                try {
                    hubs.add(DirectSocketAddress.getByAddress(a));
                } catch (Exception e) {
                    logger.warn("Failed to understand hub address: "
                            + Arrays.deepToString(tmp), e);
                }
            }
        }

        // If we don't have a hub address, we try to find one ourselves
        if (hubs.size() == 0) {
            boolean useDiscovery = properties.booleanProperty(
                    SmartSocketsProperties.DISCOVERY_ALLOWED, false);

            boolean discoveryPreferred = properties.booleanProperty(
                    SmartSocketsProperties.DISCOVERY_PREFERRED, false);

            DirectSocketAddress address = null;

            if (useDiscovery && (discoveryPreferred || hub == null)) {
                address = discoverHub(localCluster);
            }

            if (address != null) {
                hubs.add(address);
            }
        }

        // Still no address ? Give up...
        if (hubs.size() == 0) {
            // properties not set, so no central hub is available
            logger.warn("ServiceLink not created: no hub address available!");
            return;
        }

        // Sort addresses according to locality ?

        boolean force = properties
                .booleanProperty(SmartSocketsProperties.SL_FORCE);

        try {
            serviceLink = ServiceLink.getServiceLink(properties, hubs,
                    myAddresses);

            hubAddress = serviceLink.getAddress();

        } catch (Exception e) {
            logger.warn("Failed to obtain service link to hub!", e);

            if (force) {
                // FIXME!!
                logger.error("Permanent failure of servicelink! -- will exit");
                System.exit(1);
            }

            return;
        }

        if (force) {
            int retries = Math.max(1, properties
                    .getIntProperty(SmartSocketsProperties.SL_RETRIES));

            boolean connected = false;

            while (!connected && retries > 0) {
                try {
                    serviceLink.waitConnected(properties
                            .getIntProperty(SmartSocketsProperties.SL_TIMEOUT));
                    connected = true;
                } catch (Exception e) {
                    logger.warn("Failed to connect service link to hub "
                            + hubAddress, e);
                }

                retries--;
            }

            if (!connected) {
                // FIXME
                logger.error("Permanent failure of servicelink! -- will exit");
                System.exit(1);
            }
        } else {
            try {
                serviceLink.waitConnected(properties
                        .getIntProperty(SmartSocketsProperties.SL_TIMEOUT));
            } catch (Exception e) {
                logger.warn("Failed to connect service link to hub "
                        + hubAddress, e);
                return;
            }
        }

        // Check if the users want us to register any properties with the hub.
        String[] props = properties.getStringList(
                "smartsockets.register.property", ",", null);

        if (props != null && props.length > 0) {
            try {
                if (props.length == 1) {
                    serviceLink.registerProperty(props[0], "");
                } else {
                    serviceLink.registerProperty(props[0], props[1]);
                }
            } catch (Exception e) {

                if (props.length == 1) {
                    logger
                            .warn("Failed to register user property: "
                                    + props[0]);
                } else {
                    logger.warn("Failed to register user property: " + props[0]
                            + "=" + props[1]);
                }
            }
        }
    }

    private ConnectModule instantiateModule(String name) {

        if (logger.isInfoEnabled()) {
            logger.info("Loading module: " + name);
        }

        String classname = properties.getProperty(
                SmartSocketsProperties.MODULES_PREFIX + name, null);

        if (classname == null) {
            // The class implementing the module is not explicitly defined, so
            // instead we use an 'educated guess' of the form:
            //
            // smartsockets.virtual.modules.<name>.<Name>
            //
            StringBuffer tmp = new StringBuffer();
            tmp.append("ibis.smartsockets.virtual.modules.");
            tmp.append(name.toLowerCase());
            tmp.append(".");
            tmp.append(Character.toUpperCase(name.charAt(0)));
            tmp.append(name.substring(1));
            classname = tmp.toString();
        }

        if (logger.isInfoEnabled()) {
            logger.info("    class name: " + classname);
        }

        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> c;
            // Check if there is a context class loader
            if (cl != null) {
                c = cl.loadClass(classname);
            } else {
                c = Class.forName(classname);
            }

            // Check if the class we loaded is indeed a flavor of ConnectModule
            if (!ConnectModule.class.isAssignableFrom(c)) {
                logger.warn("Cannot load module " + classname + " since it is "
                        + " not a subclass of ConnectModule!");
                return null;
            }

            return (ConnectModule) c.newInstance();

        } catch (Exception e) {
            logger.info("Failed to load module " + classname, e);
        }

        return null;
    }

    private void loadModule(final String name) throws Exception {

        ConnectModule m = instantiateModule(name);
        m.init(this, name, properties, logger);

        DirectSocketAddress tmp = m.getAddresses();

        if (tmp != null) {
            if (myAddresses == null) {
                myAddresses = tmp;
            } else {
                myAddresses = DirectSocketAddress.merge(myAddresses, tmp);
            }
        }

        modules.add(m);
    }

    private void loadModules() throws Exception {

        // Get the list of modules that we should load. Note that we should
        // always load the "direct" module, since it is needed to implement the
        // others. Note that this doesn't neccesarily mean that the user wants
        // to use it though...

        String[] mods = properties.getStringList(
                SmartSocketsProperties.MODULES_DEFINE, ",", new String[0]);

        if (mods == null || mods.length == 0) {
            // Should not happen!
            throw new NoModulesDefinedException(
                    "No smartsockets modules defined!");
        }

        // Get the list of modules to skip. Note that the direct module cannot
        // be skipped completely (it is needed to implement the others).
        String[] skip = properties.getStringList(
                SmartSocketsProperties.MODULES_SKIP, ",", null);

        int count = mods.length;

        // Remove all modules that should be skipped.
        if (skip != null) {
            for (int s = 0; s < skip.length; s++) {
                for (int m = 0; m < mods.length; m++) {
                    if (skip[s].equals(mods[m])) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Skipping module " + mods[m]);
                        }
                        mods[m] = null;
                        count--;
                    }
                }
            }
        }

        if (logger.isInfoEnabled()) {

            String t = "";

            for (int i = 0; i < mods.length; i++) {
                if (mods[i] != null) {
                    t += mods[i] + " ";
                }
            }

            logger.info("Loading " + count + " modules: " + t);
        }

        if (count == 0) {
            throw new NoModulesDefinedException("No smartsockets modules "
                    + "left after filtering!");
        }

        // We start by loading the direct module. This one is always needed to
        // support the other modules, but not necessarily used by the client.
        try {
            direct = new Direct(directSocketFactory);
            direct.init(this, "direct", properties, logger);
            myAddresses = direct.getAddresses();
        } catch (Exception e) {
            logger.info("Failed to load direct module!", e);
            throw e;
        }

        if (myAddresses == null) {
            logger.info("Failed to retrieve my own address!");
            throw new NoLocalAddressException(
                    "Failed to retrieve local address!");
        }

        for (int i = 0; i < mods.length; i++) {

            if (mods[i] != null) {
                if (mods[i].equals("direct")) {
                    modules.add(direct);
                } else {
                    try {
                        loadModule(mods[i]);
                    } catch (Exception e) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Failed to load module: " + mods[i], e);
                        }

                        mods[i] = null;
                        count--;
                    }
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info(count + " modules loaded.");
        }

        if (count == 0) {
            throw new NoModulesDefinedException("Failed to load any modules");
        }
    }

    private int determineDefaultTimeout(TypedProperties p) {

        int[] tmp = new int[modules.size()];
        int totalTimeout = 0;

        for (int i = 0; i < modules.size(); i++) {
            // Get the module defined timeout
            tmp[i] = modules.get(i).getDefaultTimeout();
            totalTimeout += tmp[i];
        }

        int timeout = p.getIntProperty(SmartSocketsProperties.CONNECT_TIMEOUT,
                -1);

        if (timeout <= 0) {
            // It's up to the modules to determine their own timeout
            timeout = totalTimeout;
        } else {
            // A user-defined timeout should be distributed over the modules
            for (int i = 0; i < modules.size(); i++) {
                double t = (((double) tmp[i]) / totalTimeout) * timeout;
                modules.get(i).setTimeout((int) t);
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Total timeout set to: " + timeout);

            for (ConnectModule m : modules) {
                logger.info("  " + m.getName() + ": " + m.getTimeout());
            }
        }

        return timeout;
    }

    /**
     * Retrieve an array of the available connection modules.
     * @return array of available connection modules.
     */
    protected ConnectModule[] getModules() {
        return modules.toArray(new ConnectModule[modules.size()]);
    }

    /**
     * Retrieve an array of the available connection modules whose names are
     * listed in names.
     *
     * @param names set of modules that may be returned.
     * @return array of available connection modules whose name was included in
     * names.
     */
    protected ConnectModule[] getModules(String[] names) {

        ArrayList<ConnectModule> tmp = new ArrayList<ConnectModule>();

        for (int i = 0; i < names.length; i++) {

            boolean found = false;

            if (names[i] != null && !names[i].equals("none")) {
                for (int j = 0; j < modules.size(); j++) {
                    ConnectModule m = modules.get(j);

                    if (m.getName().equals(names[i])) {
                        tmp.add(m);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    logger.warn("Module " + names[i] + " not found!");
                }
            }
        }

        return tmp.toArray(new ConnectModule[tmp.size()]);
    }

    private void startModules() {

        ArrayList<ConnectModule> failed = new ArrayList<ConnectModule>();

        if (serviceLink == null) {
            // No servicelink, so remove all modules that depend on it....
            for (ConnectModule c : modules) {
                if (c.requiresServiceLink) {
                    failed.add(c);
                }
            }

            for (ConnectModule c : failed) {
                logger
                        .info("Module " + c.module
                                + " removed (no serviceLink)!");
                modules.remove(c);
            }

            failed.clear();
        }

        for (ConnectModule c : modules) {
            try {
                c.startModule(serviceLink);
            } catch (Exception e) {
                // Remove all modules that fail to start...
                logger.warn("Module " + c.module
                        + " did not accept serviceLink!", e);
                failed.add(c);
            }
        }

        for (ConnectModule c : failed) {
            logger.warn("Module " + c.module
                    + " removed (exception during setup)!");
            modules.remove(c);
        }

        failed.clear();
    }

    /**
     * Retrieve VirtualServerSocket that is bound to given port.
     *
     * @param port port for which to retrieve VirtualServerSocket.
     * @return VirtualServerSocket bound to port, or null if port is not in use.
     */
    public VirtualServerSocket getServerSocket(int port) {
        synchronized (serverSockets) {
            return serverSockets.get(port);
        }
    }

    /**
     * Retrieve the ConnectModule with a given name.
     *
     * @param name name of requested ConnectModule.
     * @return ConnectModule bound to name, or null is this name is not in use.
     */
    public ConnectModule findModule(String name) {

        // Direct is special, since it may be loaded without being part of the
        // modules array.
        if (name.equals("direct")) {
            return direct;
        }

        for (ConnectModule m : modules) {
            if (m.module.equals(name)) {
                return m;
            }
        }

        return null;
    }

    /**
     * Close a VirtualSocket and its related I/O Streams while ignoring
     * exceptions.
     *
     * @param s VirtualSocket to close (may be null).
     * @param out OutputStream to close (may be null).
     * @param in InputStream to close (may be null).
     */
    public static void close(VirtualSocket s, OutputStream out,
            InputStream in) {

        try {
            if (out != null) {
                out.close();
            }
        } catch (Throwable e) {
            logger.info("Failed to close OutputStream", e);
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (Throwable e) {
            logger.info("Failed to close InputStream", e);
        }

        try {
            if (s != null) {
                s.close();
            }
        } catch (Throwable e) {
            logger.info("Failed to close Socket", e);
        }
    }

    /**
     * Close a VirtualSocket and its related SocketChannel while ignoring
     * exceptions.
     *
     * @param s VirtualSocket to close (may be null).
     * @param channel SocketChannel to close (may be null).
     */
    public static void close(VirtualSocket s, SocketChannel channel) {

        if (channel != null) {
            try {
                channel.close();
            } catch (Exception e) {
                logger.info("Failed to close SocketChannel", e);
            }
        }

        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                logger.info("Failed to close Socket", e);
            }
        }
    }

    /**
     * This method implements a connect using a specific module.
     *
     * This method will return null when:
     * <p>
     * - runtime requirements do not match
     * - the module throws a NonFatalIOException
     * <p>
     * When a connection is successfully established, we wait for a accept from
     * the remote side. There are three possible outcomes:
     * <p>
     * - the connection is accepted in time
     * - the connection is not accepted in time or the connection is lost
     * - the remote side is overloaded and closes the connection
     * <p>
     * In the first case the newly created socket is returned and in the second
     * case an exception is thrown. In the last case the connection will be
     * retried using a backoff mechanism until 'timeleft' (the total timeout
     * specified by the user) is spend. Each new try behaves exactly the same as
     * the previous attempts.
     *
     * @param m ConnectModule to use in connection attempt.
     * @param target Target VirtualServerSocket.
     * @param timeout Timeout to use for this specific attempt.
     * @param timeLeft Total timeout left.
     * @param fillTimeout Should we retry until the timeout expires ?
     * @param properties Properties to use in connection setup.
     * @return a VirtualSocket if the connection setup succeeded, null
     * otherwise.
     * @throws IOException a non-transient error occured (i.e., target port does
     * not exist).
     * @throws NonFatalIOException a transient error occured (i.e., timeout).
     */
    private VirtualSocket createClientSocket(ConnectModule m,
            VirtualSocketAddress target, int timeout, int timeLeft,
            boolean fillTimeout, Map<String, Object> properties)
            throws IOException, NonFatalIOException {

        int backoff = 1000;

        if (!m.matchRuntimeRequirements(properties)) {
            if (conlogger.isInfoEnabled()) {
                conlogger.warn("Failed: module " + m.module
                        + " may not be used to set " + "up connection to "
                        + target);
            }

            m.connectNotAllowed();
            return null;
        }

        if (conlogger.isDebugEnabled()) {
            conlogger.debug("Using module " + m.module + " to set up "
                    + "connection to " + target + " timeout = " + timeout
                    + " timeleft = " + timeLeft);
        }

        // We now try to set up a connection. Normally we may not exceed the
        // timeout, but when we succesfully create a connection we are allowed
        // to extend this time to timeLeft (either to wait for an accept, or to
        // retry after a TargetOverLoaded exception). Note that any exception
        // other than ModuleNotSuitable or TargetOverloaded is passed to the
        // user.
        VirtualSocket vs = null;
        int overloaded = 0;

        long start = System.currentTimeMillis();

        boolean lastAttempt = false;

        while (true) {

            long t = System.currentTimeMillis() - start;

            // Check if we ran out of time. If so, the throw a target overloaded
            // exception or a timeout exception depending on the value of the
            // overloaded counter.
            if (t >= timeLeft) {
                if (conlogger.isDebugEnabled()) {
                    conlogger.debug("Timeout while using module " + m.module
                            + " to set up " + "connection to " + target
                            + " timeout = " + timeout + " timeleft = "
                            + timeLeft + " t = " + t);
                }

                if (overloaded > 0) {
                    m.connectRejected(t);
                    throw new TargetOverloadedException("Failed to create "
                            + "virtual connection to " + target + " within "
                            + timeLeft + " ms. (Target overloaded "
                            + overloaded + " times)");
                } else {
                    m.connectFailed(t);
                    throw new SocketTimeoutException("Timeout while creating"
                            + " connection to " + target);
                }
            }

            try {
                vs = m.connect(target, timeout, properties);
            } catch (NonFatalIOException e) {
                long end = System.currentTimeMillis();

                // Just print and try the next module...
                if (conlogger.isInfoEnabled()) {
                    conlogger.info("Module " + m.module + " failed to connect "
                            + "to " + target + " after " + (end - start)
                            + " ms.): " + e.getMessage());
                }

                m.connectFailed(end - start);

                throw e;

                // NOTE: The modules may also throw IOExceptions for
                // non-transient errors (i.e., port not found). These are
                // forwarded to the user.
            }

            t = System.currentTimeMillis() - start;

            if (vs != null) {
                // We now have a connection to the correct machine and must wait
                // for an accept from the serversocket. Since we don't have to
                // try any other modules, we are allowed to spend all of the
                // time that is left. Therefore, we start by calculating a new
                // timeout here, which is based on the left over time for the
                // entire connect call, minus the time we have spend so far in
                // this connect. This is the timeout we pass to 'waitForAccept'.
                int newTimeout = (int) (timeLeft - t);

                if (newTimeout <= 0) {
                    // Bit of a hack. If we run out of time at the last moment
                    // we allow some extra time to finish the connection setup.
                    // TODO: should we do this ?
                    newTimeout = 1000;
                }

                if (conlogger.isInfoEnabled()) {
                    conlogger.info(getVirtualAddressAsString() + ": Success "
                            + m.module + " connected to " + target
                            + " now waiting for accept (for max. " + newTimeout
                            + " ms.)");
                }

                try {
                    vs.waitForAccept(newTimeout);
                    vs.setTcpNoDelay(false);

                    long end = System.currentTimeMillis();

                    if (conlogger.isInfoEnabled()) {
                        conlogger.info(getVirtualAddressAsString()
                                + ": Success " + m.module + " connected to "
                                + target + " (time = " + (end - start)
                                + " ms.)");
                    }

                    m.connectSucces(end - start);
                    return vs;

                } catch (TargetOverloadedException e) {
                    // This is always allowed.
                    if (conlogger.isDebugEnabled()) {
                        conlogger.debug("Connection failed, target " + target
                                + " overloaded (" + overloaded
                                + ") while using " + " module " + m.module);
                    }

                    overloaded++;

                } catch (IOException e) {

                    if (conlogger.isDebugEnabled()) {
                        conlogger.debug("Connection failed, target " + target
                                + ", got exception (" + e.getMessage()
                                + ") while using " + " module " + m.module);
                    }

                    if (!fillTimeout) {
                        m.connectFailed(System.currentTimeMillis() - start);

                        // We'll only retry if 'fillTimeout' is true
                        throw e;
                    }
                }

                // The target has refused our connection. Since we have
                // obviously found a working module, we will not return.
                // Instead we will retry using a backoff algorithm until
                // we run out of time...
                t = System.currentTimeMillis() - start;

                m.connectRejected(t);

                int leftover = (int) (timeLeft - t);

                if (!lastAttempt && leftover > 0) {

                    int sleeptime = 0;

                    if (backoff < leftover) {
                        // Use a randomized sleep value to ensure the attempts
                        // are distributed.
                        sleeptime = random.nextInt(backoff);
                    } else {
                        sleeptime = leftover;
                        lastAttempt = true;
                    }

                    // System.err.println("Backoff = " + backoff +
                    // " Leftover = " + leftover + " Sleep time = " +
                    // sleeptime);

                    if (sleeptime > 0) {
                        try {
                            Thread.sleep(sleeptime);
                        } catch (Exception x) {
                            // ignored
                        }
                    }

                    if (leftover < 500) {
                        // We're done!
                        timeLeft = 0;
                    } else {
                        backoff *= 2;
                    }

                } else {
                    // We're done
                    timeLeft = 0;
                }
            }
        }
    }

    private String[] getNames(ConnectModule[] modules) {
        String[] names = new String[modules.length];

        for (int n = 0; n < modules.length; n++) {
            names[n] = modules[n].getName();
        }

        return names;
    }

    /**
     * This method loops over and array of connection modules, trying to setup
     * a connection with each of them each in turn.
     * <p>
     * This method returns when:<br>
     * - a connection is established<b>
     * - a non-transient error occurs (i.e. remote port not found)<b>
     * - all modules have failed<b>
     * - a timeout occurred<b>
     *
     * @param target Target VirtualServerSocket.
     * @param order ConnectModules in the order in which they should be tried.
     * @param timeouts Timeouts for each of the modules.
     * @param totalTimeout Total timeout for the connection setup.
     * @param timing Array in which to record the time required by each module.
     * @param fillTimeout Should we retry until the timeout expires ?
     * @param properties Properties to use in connection setup.
     * @return a VirtualSocket if the connection setup succeeded, null
     * otherwise.
     * @throws IOException a non-transient error occurred (i.e., target port
     * does not exist on receiver).
     * @throws NoSuitableModuleException No module could create the connection.
     */
    private VirtualSocket createClientSocket(VirtualSocketAddress target,
            ConnectModule[] order, int[] timeouts, int totalTimeout,
            long[] timing, boolean fillTimeout, Map<String, Object> prop)
            throws IOException, NoSuitableModuleException {

        Throwable[] exceptions = new Throwable[order.length];

        try {
            int timeLeft = totalTimeout;

            VirtualSocket vs = null;

            // Now try the remaining modules (or all of them if we weren't
            // using the cache in the first place...)
            for (int i = 0; i < order.length; i++) {

                ConnectModule m = order[i];
                int timeout = (timeouts != null ? timeouts[i] : m.getTimeout());

                long start = System.currentTimeMillis();

                /*
                 * if (timing != null) { timing[1 + i] = System.nanoTime();
                 *
                 * if (i > 0) { prop.put("direct.detailed.timing.ignore", null);
                 * } }
                 */

                try {
                    vs = createClientSocket(m, target, timeout, timeLeft,
                            fillTimeout, prop);
                } catch (NonFatalIOException e) {
                    // Store the exeception and continue with the next module
                    exceptions[i] = e;
                }

                /*
                 * if (timing != null) { timing[1 + i] = System.nanoTime() -
                 * timing[1 + i]; }
                 */

                if (vs != null) {
                    if (i > 0) {
                        // We managed to connect, but not with the first module,
                        // so we remember this to speed up later connections.
                        clusters.succes(target, m);
                    }
                    return vs;
                }

                if (order.length > 1 && i < order.length - 1) {

                    timeLeft -= System.currentTimeMillis() - start;

                    if (timeLeft <= 0) {
                        // NOTE: This can only happen when a module breaks
                        // the rules (defensive programming).
                        throw new NoSuitableModuleException("Timeout during "
                                + " connect to " + target, getNames(order),
                                exceptions);
                    }
                }
            }

            if (logger.isInfoEnabled()) {
                logger.info("No suitable module found to connect to " + target);
            }

            // No suitable modules found...
            throw new NoSuitableModuleException("No suitable module found to"
                    + " connect to " + target + " (timeouts="
                    + Arrays.toString(timeouts) + ", fillTimeout="
                    + fillTimeout + ")", getNames(order), exceptions);

        } finally {
            if (timing != null && prop != null) {
                timing[0] = System.nanoTime() - timing[0];
                prop.remove("direct.detailed.timing.ignore");
            }
        }
    }

    // Distribute a given timeout over a number of modules, taking the relative
    // sizes of the default module timeouts into account.
    private int[] distributesTimeout(int timeout, int[] timeouts,
            ConnectModule[] modules) {

        if (timeouts == null) {
            timeouts = new int[modules.length];
        }

        for (int i = 0; i < modules.length; i++) {
            double t = (((double) modules[i].getTimeout()) / DEFAULT_TIMEOUT);
            timeouts[i] = (int) (t * timeout);
        }

        return timeouts;
    }

    /**
     * Create a connection to the VirtualServerSocket at target.
     *
     * This method will attempt to setup a connection to the VirtualServerSocket
     * at target within the given timeout. Using the prop parameter, properties
     * can be specified that modify the connection setup behavior.
     *
     * @param target Address of target VirtualServerSocket.
     * @param timeout The maximum timeout for the connection setup in
     * milliseconds. When the timeout is zero the call may block indefinitely,
     * while a negative timeout will revert to the default value.
     * @param prop Properties that modify the connection setup behavior (may be
     *        null).
     * @return a VirtualSocket when the connection setup was successful.
     * @throws IOException when the connection setup failed.
     */
    public VirtualSocket createClientSocket(VirtualSocketAddress target,
            int timeout, Map<String, Object> prop) throws IOException {
        return createClientSocket(target, timeout, false, prop);
    }

    /**
     * Create a connection to the VirtualServerSocket at target.
     *
     * This method will attempt to setup a connection to the VirtualServerSocket
     * at target within the given timeout. Using the prop parameter, properties
     * can be specified that modify the connection setup behavior.
     *
     * @param target Address of target VirtualServerSocket.
     * @param timeout The maximum timeout for the connection setup in
     * milliseconds. When the timeout is zero the call may block indefinitely,
     * while a negative timeout will revert to the default value.
     * @param fillTimeout Should we retry until the timeout expires ?
     * @param prop Properties that modify the connection setup behavior (may be
     *        null).
     * @return a VirtualSocket when the connection setup was successful.
     * @throws IOException when the connection setup failed.
     */
    public VirtualSocket createClientSocket(VirtualSocketAddress target,
            int timeout, boolean fillTimeout, Map<String, Object> prop)
            throws IOException {

        // Note: it's up to the user to ensure that this thing is large enough!
        // i.e., it should be of size 1+modules.length
        if (conlogger.isDebugEnabled()) {
            conlogger.debug("createClientSocket(" + target + ", " + timeout
                    + ", " + fillTimeout + ", " + prop + ")");
        }

        /*
         * long [] timing = null;
         *
         * if (prop != null) { // Note: it's up to the user to ensure that this
         * thing is large // enough! i.e., it should be of size 1+modules.length
         * timing = (long[]) prop.get("virtual.detailed.timing");
         *
         * if (timing != null) { timing[0] = System.nanoTime(); } }
         */

        // Check the timeout here. If it is not set, we will use the default
        if (timeout <= 0) {
            timeout = DEFAULT_TIMEOUT;
        }

        ConnectModule[] order = clusters.getOrder(target);

        int timeLeft = timeout;
        int[] timeouts = null;

        boolean lastAttempt = false;
        int backoff = 250;

        NoSuitableModuleException exception = null;
        LinkedList<NoSuitableModuleException> exceptions = null;

        if (DETAILED_EXCEPTIONS) {
            exceptions = new LinkedList<NoSuitableModuleException>();
        }

        do {
            if (timeLeft <= DEFAULT_TIMEOUT) {
                // determine timeout for each module. We assume that this time
                // is used completely and therefore only do this once.
                timeouts = distributesTimeout(timeLeft, timeouts, order);
                fillTimeout = false;
            }

            long start = System.currentTimeMillis();

            try {
                return createClientSocket(target, order, timeouts, timeLeft,
                /* timing */null, fillTimeout, prop);
            } catch (NoSuitableModuleException e) {
                // All modules where tried and failed. It now depends on the
                // user if he would like to try another round or give up.
                if (conlogger.isDebugEnabled()) {
                    conlogger.debug("createClientSocket failed. Will "
                            + (fillTimeout ? "" : "NOT ") + "retry");
                }

                if (DETAILED_EXCEPTIONS) {
                    exceptions.add(e);
                } else {
                    exception = e;
                }
            }

            timeLeft -= System.currentTimeMillis() - start;

            if (!lastAttempt && fillTimeout && timeLeft > 0) {

                int sleeptime = 0;

                if (backoff < timeLeft) {
                    sleeptime = random.nextInt(backoff);
                } else {
                    sleeptime = timeLeft;
                    lastAttempt = true;
                }

                if (sleeptime >= timeLeft) {
                    // In the last attempt we sleep half a second shorter.
                    // This allows us to attempt a connection setup.
                    if (sleeptime > 500) {
                        sleeptime -= 500;
                    } else {
                        // We're done!
                        sleeptime = 0;
                        fillTimeout = false;
                    }
                }

                if (sleeptime > 0) {
                    try {
                        Thread.sleep(sleeptime);
                    } catch (Exception x) {
                        // ignored
                    }
                }

                backoff *= 2;
            } else {
                fillTimeout = false;
            }

        } while (fillTimeout);

        if (DETAILED_EXCEPTIONS) {
            throw new NoSuitableModuleException("No suitable module found to "
                    + "connect to " + target + "(timeout=" + timeout
                    + ", fillTimeout=" + fillTimeout + ")", exceptions);
        } else {
            throw exception;
        }
    }

    private int getPort() {

        // TODO: should this be random ?
        synchronized (serverSockets) {
            while (true) {
                if (!serverSockets.containsKey(nextPort)) {
                    return nextPort++;
                } else {
                    nextPort++;
                }
            }
        }
    }

    /**
     * Create a new VirtualServerSocket bound the the given port.
     *
     * This method will create a new VirtualServerSocket bound the the given,
     * and using the specified backlog. If port is zero or negative, a valid
     * unused port number will be generated. If the backlog is zero or negative,
     * the default value will be used.
     * <p>
     * The properties parameter used to modify the connection setup behavior of
     * the new VirtualServerSocket.
     *
     * @param port The port number to use.
     * @param backlog The maximum number of pending connections this
     * VirtualServerSocket will allow.
     * @param retry Retry if the VirtualServerSocket creation fails.
     * @param properties Properties that modify the connection setup behavior
     *        (may be null).
     * @return the new VirtualServerSocket, or null if the creation failed.
     */
    public VirtualServerSocket createServerSocket(int port, int backlog,
            boolean retry, java.util.Properties properties) {

        // Fix: extract all string properties, don't use the properties object
        // as a map! --Ceriel
        HashMap<String, Object> props = new HashMap<String, Object>();
        if (properties != null) {
            // Fix: stringPropertyNames method isn't available on android, so
            // added a cast --Roelof
            // for (String s : properties.stringPropertyNames()) {
            for (Object string : properties.keySet()) {
                props.put((String) string, properties
                        .getProperty((String) string));
            }
        }
        VirtualServerSocket result = null;

        while (result == null) {
            try {
                // Did not pass on properties. Fixed. --Ceriel
                // result = createServerSocket(port, backlog, null);
                result = createServerSocket(port, backlog, props);
            } catch (Exception e) {
                // retry
                if (logger.isDebugEnabled()) {
                    logger.debug("Failed to open serversocket on port " + port
                            + " (will retry): ", e);
                }
            }
        }

        return result;
    }

    /**
     * Create a new unbound VirtualServerSocket.
     *
     * This method will create a new VirtualServerSocket that is not yet bound
     * to a port.
     * <p>
     * The properties parameter used to modify the connection setup behavior of
     * the new VirtualServerSocket.
     *
     * @param props Properties that modify the connection setup behavior
     *        (may be null).
     * @return the new VirtualServerSocket.
     * @throws IOException the VirtualServerSocket creation has failed.
     */
    public VirtualServerSocket createServerSocket(Map<String, Object> props)
            throws IOException {
        return new VirtualServerSocket(this, DEFAULT_ACCEPT_TIMEOUT, props);
    }

    /**
     * Bind an unbound VirtualServerSocket to a port.
     *
     * @param vss The VirtualServerSocket to bind to the port.
     * @param port The port to bind the VirtualServerSocket to.
     * @throws BindException Failed to bind the port to the VirtualServerSocket.
     */
    protected void bindServerSocket(VirtualServerSocket vss, int port)
            throws BindException {

        synchronized (serverSockets) {
            if (serverSockets.containsKey(port)) {
                throw new BindException("Port " + port + " already in use!");
            }

            serverSockets.put(port, vss);
        }
    }

    /**
     * Create a new VirtualServerSocket bound the the given port.
     *
     * This method will create a new VirtualServerSocket bound the the given,
     * and using the specified backlog. If port is zero or negative, a valid
     * unused port number will be generated. If the backlog is zero or negative,
     * the default value will be used.
     * <p>
     * The properties parameter used to modify the connection setup behavior of
     * the new VirtualServerSocket.
     *
     * @param port The port number to use.
     * @param backlog The maximum number of pending connections this
     * VirtualServerSocket will allow.
     * @param properties Properties that modify the connection setup behavior
     *        (may be null).
     * @return the new VirtualServerSocket, or null if the creation failed.
     */


    public VirtualServerSocket createServerSocket(int port, int backlog,
            Map<String, Object> properties) throws IOException {

        if (backlog <= 0) {
            backlog = DEFAULT_BACKLOG;
        }

        if (port <= 0) {
            port = getPort();
        }

        if (logger.isInfoEnabled()) {
            logger.info("Creating VirtualServerSocket(" + port + ", " + backlog
                    + ", " + properties + ")");
        }

        synchronized (serverSockets) {
            if (serverSockets.containsKey(port)) {
                throw new BindException("Port " + port + " already in use!");
            }

            VirtualSocketAddress a = new VirtualSocketAddress(myAddresses,
                    port, hubAddress, clusters.localCluster());

            VirtualServerSocket vss = new VirtualServerSocket(this, a, port,
                    backlog, DEFAULT_ACCEPT_TIMEOUT, properties);

            serverSockets.put(port, vss);
            return vss;
        }
    }

    // TODO: hide this thing ?
    /**
     * Retrieve the ServiceLink that is connected to the hub.
     * @return the ServiceLink that is connected to the Hub
     */
    public ServiceLink getServiceLink() {
        return serviceLink;
    }

    /**
     * Retrieve the DirectSocketAddress of this machine.
     * @return the DirectSocketAddress of this machine.
     */
    public DirectSocketAddress getLocalHost() {
        return myAddresses;
    }

    /**
     * Retrieve the name of the local cluster.
     * @return the name of the local cluster.
     */
    public String getLocalCluster() {
        return clusters.localCluster();
    }

    /**
     * Retrieve the DirectSocketAddress of the hub this VirtualSocketFactory is
     * connected to.
     * @return the DirectSocketAddress of the hub this VirtualSocketFactory is
     * connected to.
     */
    public DirectSocketAddress getLocalHub() {
        return hubAddress;
    }

    /**
     * Provide a list of hub addresses to the VirtualSocketFactory.
     * @param hubs The hub addresses.
     */
    public void addHubs(DirectSocketAddress... hubs) {
        if (hub != null) {
            hub.addHubs(hubs);
        } else if (serviceLink != null) {
            serviceLink.addHubs(hubs);
        }
    }

    /**
     * Provide a list of hub addresses to the VirtualSocketFactory.
     * @param hubs The hub addresses.
     */
    public void addHubs(String... hubs) {
        if (hub != null) {
            hub.addHubs(hubs);
        } else if (serviceLink != null) {
            serviceLink.addHubs(hubs);
        }
    }

    /**
     * Retrieve the known hub addresses.
     * @return an array containing the known hub addresses.
     */
    public DirectSocketAddress[] getKnownHubs() {

        if (hub != null) {
            return hub.knownHubs();
        } else if (serviceLink != null) {
            try {
                return serviceLink.hubs();
            } catch (IOException e) {
                logger.info("Failed to retrieve hub list!", e);
            }
        }

        return null;
    }

    /**
     * Shutdown the VirtualSocketFactory.
     */
    public void end() {

        if (printer != null) {
            printer.adjustInterval(-1);
        }

        if (printStatistics) {
            printStatistics(statisticPrefix + " [EXIT]");
        }

        if (serviceLink != null) {
            serviceLink.setDone();
        }

        if (hub != null) {
            hub.end();
        }
    }

    /**
     * Close a port.
     * @param port the port to close.
     */
    protected void closed(int port) {
        synchronized (serverSockets) {
            serverSockets.remove(Integer.valueOf(port));
        }
    }

    /**
     * Retrieve a previously created (and configured) VirtualSocketFactory.
     *
     * @param name the VirtualSocketFactory to retrieve.
     * @return the VirtualSocketFactory, or null if it does not exist.
     */
    public static synchronized VirtualSocketFactory getSocketFactory(
            String name) {

        return factories.get(name);
    }


    /**
     * Get a VirtualSocketFactory, either by retrieving or by creating it.
     *
     * If the VirtualSocketFactory already existed, the provided properties will
     * be compared to those of the existing VirtualSocketFactory to ensure that
     * they are the same. If they are not, an InitializationException will be
     * thrown.
     * <p>
     * If the VirtualSocketFactory did not exist, it will be created and stored
     * under name for later lookup.
     *
     * @param name the name of the VirtualSocketFactory to retrieve or create.
     * @param p A set of properties that configure the VirtualSocketFactory.
     * @param addDefaults Add the default properties to the configuration.
     * @return the retrieved or created VirtualSocketFactory.
     * @throws InitializationException if the VirtualSocketFactory could not be
     * created or received.
     */
    public static synchronized VirtualSocketFactory getOrCreateSocketFactory(
            String name, java.util.Properties p, boolean addDefaults)
            throws InitializationException {
        VirtualSocketFactory result = factories.get(name);

        if (result == null) {
            result = createSocketFactory(p, addDefaults);
            factories.put(name, result);
        } else {

            TypedProperties typedProperties = new TypedProperties();

            if (addDefaults) {
                typedProperties.putAll(SmartSocketsProperties
                        .getDefaultProperties());
            }

            if (p != null) {
                typedProperties.putAll(p);
            }

            if (!typedProperties.equals(result.properties)) {
                throw new InitializationException("could not retrieve existing"
                        + " factory, properties are not equal");
            }
        }

        return result;
    }

    /**
     * Register an existing VirtualSocketFactory under a different name.
     * @param name The name to register the VirtualSocketFactory.
     * @param factory The VirtualSocketFactory to register.
     */
    public static synchronized void registerSocketFactory(String name,
            VirtualSocketFactory factory) {
        factories.put(name, factory);
    }

    /**
     * Retrieve a VirtualSocketFactory using the default configuration.
     *
     * @return a VirtualSocketFactory using the default configuration.
     * @throws InitializationException the VirtualSocketFactory could not be
     * retrieved.
     */
    public static synchronized VirtualSocketFactory getDefaultSocketFactory()
            throws InitializationException {

        if (defaultFactory == null) {
            defaultFactory = createSocketFactory();
        }

        return defaultFactory;
    }

    /**
     * Create a VirtualSocketFactory using the default configuration.
     *
     * @return a VirtualSocketFactory using the default configuration.
     * @throws InitializationException the VirtualSocketFactory could not be
     * created.
     */
    public static VirtualSocketFactory createSocketFactory()
            throws InitializationException {

        return createSocketFactory((java.util.Properties) null, true);
    }

    /**
     * Create a VirtualSocketFactory using the configuration provided in p.
     *
     * @param p the configuration to use (as a key-value map).
     * @param addDefaults Should the default configuration be added ?
     * @return a VirtualSocketFactory using the configuration in p.
     * @throws InitializationException the VirtualSocketFactory could not be
     * created.
     */
    public static VirtualSocketFactory createSocketFactory(Map<String, ?> p,
            boolean addDefaults) throws InitializationException {
        return createSocketFactory(new TypedProperties(p), addDefaults);
    }

    /**
     * Create a VirtualSocketFactory using the configuration provided in
     * properties.
     *
     * @param properties the configuration to use.
     * @param addDefaults Should the default configuration be added ?
     * @return a VirtualSocketFactory using the configuration in properties.
     * @throws InitializationException the VirtualSocketFactory could not be
     * created.
     */
    public static VirtualSocketFactory createSocketFactory(
            java.util.Properties properties, boolean addDefaults)
            throws InitializationException {

        TypedProperties typedProperties = new TypedProperties();

        if (addDefaults) {
            typedProperties.putAll(SmartSocketsProperties
                    .getDefaultProperties());
        }

        if (properties != null) {
            typedProperties.putAll(properties);
        }

        if (typedProperties.booleanProperty(SmartSocketsProperties.START_HUB,
                false)) {

            boolean allowSSHForHub = typedProperties.booleanProperty(
                    SmartSocketsProperties.HUB_SSH_ALLOWED, true);

            if (allowSSHForHub) {
                typedProperties.setProperty(SmartSocketsProperties.SSH_IN,
                        "true");
                // Do we need this one ?
                // typedProperties.setProperty(SmartSocketsProperties.SSH_OUT,
                // "true");
            }
        }

        VirtualSocketFactory factory = new VirtualSocketFactory(
                DirectSocketFactory.getSocketFactory(typedProperties),
                typedProperties);

        return factory;
    }

    /**
     * Retrieve the contact address of this VirtualSocketFactory.
     *
     * @return a VirtualSocketAddress containing the contact address of this
     * VirtualSocketFactory.
     */
    public VirtualSocketAddress getLocalVirtual() {
        return localVirtualAddress;
    }

    /**
     * Retrieve the contact address of this VirtualSocketFactory.
     *
     * @return a String containing the contact address of this
     * VirtualSocketFactory.
     */
    public String getVirtualAddressAsString() {
        return localVirtualAddressAsString;
    }

    /**
     * Print statistics on the connections created by this VirtualSocketFactory.
     *
     * Every line printed will prepended with the given prefix.
     *
     * @param prefix the prefix to use when printing.
     */
    public void printStatistics(String prefix) {

        if (statslogger.isInfoEnabled()) {
            statslogger.info(prefix + " === VirtualSocketFactory ("
                    + modules.size() + " / "
                    + (serviceLink == null ? "No SL" : "SL") + ") ===");

            for (ConnectModule c : modules) {
                c.printStatistics(prefix);
            }

            if (serviceLink != null) {
                serviceLink.printStatistics(prefix);
            }
        }
    }
}
