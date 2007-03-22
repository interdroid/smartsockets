package smartsockets.virtual;

import ibis.util.ThreadPool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import smartsockets.Properties;
import smartsockets.direct.DirectSocketAddress;
import smartsockets.discovery.Discovery;
import smartsockets.hub.servicelink.ServiceLink;
import smartsockets.util.TypedProperties;
import smartsockets.virtual.modules.ConnectModule;

/**
 * This class implements a 'virtual' socket factory.
 * 
 * @author Jason Maassen
 * @version 1.0 Jan 30, 2006
 * @since 1.0
 * 
 */
public class VirtualSocketFactory {

    private static class StatisticsPrinter implements Runnable {

        private int timeout;

        StatisticsPrinter(int timeout) {
            this.timeout = timeout;
        }

        public void run() {

            while (true) {

                int t = getTimeout();

                try {
                    Thread.sleep(t);
                } catch (Exception e) {
                    // ignore
                }

                try {
                    for (String s : VirtualSocketFactory.factories.keySet()) {
                        VirtualSocketFactory.factories.get(s)
                                .printStatistics(s);
                    }
                } catch (Exception e) {
                    // TODO: IGNORE ?
                }
            }
        }

        private synchronized int getTimeout() {
            return timeout;
        }

        public synchronized void adjustInterval(int interval) {
            if (interval < timeout) {
                timeout = interval;
            }
        }
    }

    private static final Map<String, VirtualSocketFactory> factories = new HashMap<String, VirtualSocketFactory>();

    private static VirtualSocketFactory defaultFactory = null;

    private static StatisticsPrinter printer = null;

    protected static Logger logger = ibis.util.GetLogger
            .getLogger("smartsockets.virtual.misc");

    protected static Logger conlogger = ibis.util.GetLogger
            .getLogger("smartsockets.virtual.connect");

    private static final Logger statslogger = ibis.util.GetLogger
            .getLogger("smartsockets.statistics");

    private final ArrayList<ConnectModule> modules = new ArrayList<ConnectModule>();

    private final TypedProperties properties;

    private final int DEFAULT_BACKLOG;

    private final int DEFAULT_TIMEOUT;

    // private final int DISCOVERY_PORT;

    private final HashMap<Integer, VirtualServerSocket> serverSockets = new HashMap<Integer, VirtualServerSocket>();

    private int nextPort = 3000;

    private DirectSocketAddress myAddresses;

    private DirectSocketAddress hubAddress;

    private VirtualSocketAddress localVirtualAddress;

    private String localVirtualAddressAsString;

    private ServiceLink serviceLink;

    private VirtualClusters clusters;

    private VirtualSocketFactory(TypedProperties p)
            throws InitializationException {

        if (logger.isInfoEnabled()) {
            logger.info("Creating VirtualSocketFactory");
        }

        properties = p;

        DEFAULT_BACKLOG = p.getIntProperty(Properties.BACKLOG);
        DEFAULT_TIMEOUT = p.getIntProperty(Properties.TIMEOUT);
        // DISCOVERY_PORT =

        // NOTE: order is VERY important here!
        loadModules();

        if (modules.size() == 0) {
            logger.info("Failed to load any modules!");
            throw new InitializationException("Failed to load any modules!");
        }

        String localCluster = p.getProperty(Properties.CLUSTER_MEMBER, null);

        createServiceLink(localCluster);
        startModules();

        if (modules.size() == 0) {
            logger.info("Failed to start any modules!");
            throw new InitializationException("Failed to load any modules!");
        }

        loadClusterDefinitions();

        localVirtualAddress = new VirtualSocketAddress(myAddresses, 0,
                hubAddress, clusters.localCluster());

        localVirtualAddressAsString = localVirtualAddress.toString();
    }

    private void loadClusterDefinitions() {
        clusters = new VirtualClusters(this, properties, getModules());
    }

    private void createServiceLink(String localCluster) {

        DirectSocketAddress address = null;

        // Check if the proxy address was passed as a property.
        String tmp = properties.getProperty(Properties.HUB_ADDRESS);

        if (tmp != null) {
            try {
                address = DirectSocketAddress.getByAddress(tmp);
            } catch (Exception e) {
                logger.warn("Failed to understand proxy address: " + tmp, e);
            }
        }

        boolean useDiscovery = properties.booleanProperty(
                Properties.DISCOVERY_ALLOWED, false);

        boolean discoveryPreferred = properties.booleanProperty(
                Properties.DISCOVERY_PREFERRED, false);

        // Check if we can discover the proxy address using UDP multicast.
        if (useDiscovery && (discoveryPreferred || address == null)) {
            if (logger.isInfoEnabled()) {
                logger
                        .info("Attempting to discover proxy using UDP multicast...");
            }

            int port = properties.getIntProperty(Properties.DISCOVERY_PORT);
            int time = properties.getIntProperty(Properties.DISCOVERY_TIMEOUT);

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
        }

        // Still no address ? Give up...
        if (address == null) {
            // properties not set, so no central hub is available
            // if (logger.isInfoEnabled()) {
            // System.out.println("ServiceLink not created: no hub address
            // available!");
            logger.info("ServiceLink not created: no hub address available!");
            // }
            return;
        }

        try {
            serviceLink = ServiceLink.getServiceLink(properties, address,
                    myAddresses);

            hubAddress = serviceLink.getAddress();

            if (true) {
                serviceLink.waitConnected(10000);
            }
        } catch (Exception e) {
            logger.warn("Failed to connect service link to hub!", e);
            return;
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

    private ConnectModule loadModule(String name) {

        if (logger.isInfoEnabled()) {
            logger.info("Loading module: " + name);
        }

        String classname = properties.getProperty(Properties.MODULES_PREFIX
                + name, null);

        if (classname == null) {
            // The class implementing the module is not explicitly defined, so
            // instead we use an 'educated guess' of the form:
            //
            // smartsockets.virtual.modules.<name>.<Name>
            //            
            StringBuffer tmp = new StringBuffer();
            tmp.append("smartsockets.virtual.modules.");
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
            Class c = cl.loadClass(classname);

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

    private void loadModules() {
        // Get the list of modules that we should load...
        String[] mods = properties.getStringList(Properties.MODULES_DEFINE,
                ",", new String[0]);

        int count = mods.length;

        if (mods == null || mods.length == 0) {
            logger.info("No smartsockets modules defined!");
            return;
        }

        String[] skip = properties.getStringList(Properties.MODULES_SKIP, ",",
                null);

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

        String t = "";

        for (int i = 0; i < mods.length; i++) {
            if (mods[i] != null) {
                t += mods[i] + " ";
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Loading " + count + " modules: " + t);
        }

        for (int i = 0; i < mods.length; i++) {

            if (mods[i] != null) {
                try {
                    ConnectModule m = loadModule(mods[i]);
                    m.init(this, mods[i], properties, logger);

                    DirectSocketAddress tmp = m.getAddresses();

                    if (tmp != null) {
                        if (myAddresses == null) {
                            myAddresses = tmp;
                        } else {
                            myAddresses = DirectSocketAddress.merge(
                                    myAddresses, tmp);
                        }
                    }

                    modules.add(m);
                } catch (Exception e) {
                    logger.info("Failed to load module: " + mods[i], e);
                    mods[i] = null;
                    count--;
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info(count + " modules loaded.");
        }
    }

    protected ConnectModule[] getModules() {
        return modules.toArray(new ConnectModule[modules.size()]);
    }

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

    public VirtualServerSocket getServerSocket(int port) {
        synchronized (serverSockets) {
            return serverSockets.get(port);
        }
    }

    public ConnectModule findModule(String name) {

        for (ConnectModule m : modules) {
            if (m.module.equals(name)) {
                return m;
            }
        }

        return null;
    }

    public static void close(VirtualSocket s, OutputStream out, InputStream in) {

        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
            // ignore
        }

        try {
            if (s != null) {
                s.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private VirtualSocket createClientSocket(ConnectModule m,
            VirtualSocketAddress target, int timeout,
            Map<String, Object> properties) throws IOException {

        if (m.matchRuntimeRequirements(properties)) {
            if (conlogger.isDebugEnabled()) {
                conlogger.debug("Using module " + m.module + " to set up "
                        + "connection to " + target + " timeout = " + timeout);
            }

            long start = System.currentTimeMillis();

            try {

                VirtualSocket vs = m.connect(target, timeout, properties);

                long end = System.currentTimeMillis();

                // TODO: move to ibis ?
                if (vs != null) {
                    vs.setTcpNoDelay(true);
                    if (conlogger.isInfoEnabled()) {
                        conlogger.info(getVirtualAddressAsString()
                                + ": Sucess " + m.module + " connected to "
                                + target + " (time = " + (end - start)
                                + " ms.)");
                    }

                    m.success(end - start);
                    return vs;
                }

                m.failed(end - start);

            } catch (ModuleNotSuitableException e) {

                long end = System.currentTimeMillis();

                // Just print and try the next module...
                if (conlogger.isInfoEnabled()) {
                    conlogger.info(getVirtualAddressAsString() + ": Failed "
                            + m.module + " not suitable (time = "
                            + (end - start) + " ms.)");
                }

                m.failed(end - start);
            }
            // NOTE: other exceptions are forwarded to the user!
        } else {
            if (conlogger.isInfoEnabled()) {
                conlogger.warn("Failed: module " + m.module
                        + " may not be used to set " + "up connection to "
                        + target);
            }

            m.notAllowed();
        }

        return null;
    }

    public VirtualSocket createClientSocket(VirtualSocketAddress target,
            int timeout, Map<String, Object> prop) throws IOException {

        // Note: it's up to the user to ensure that this thing is large enough!
        // i.e., it should be of size 1+modules.length
        long[] timing = null;

        if (prop != null) {
            timing = (long[]) prop.get("virtual.detailed.timing");

            if (timing != null) {
                timing[0] = System.nanoTime();
            }
        }

        try {

            int notSuitableCount = 0;

            if (timeout < 0) {
                timeout = DEFAULT_TIMEOUT;
            }

            ConnectModule[] order = clusters.getOrder(target);

            int timeLeft = timeout;
            int partialTimeout;

            if (timeout > 0 && order.length > 0) {
                partialTimeout = (timeout / order.length);
            } else if (order.length > 0) {
                partialTimeout = DEFAULT_TIMEOUT;
            } else {
                partialTimeout = 0;
            }

            // Now try the remaining modules (or all of them if we weren't
            // using the cache in the first place...)
            for (int i = 0; i < order.length; i++) {

                ConnectModule m = order[i];

                long start = System.currentTimeMillis();

                if (timing != null) {
                    timing[1 + i] = System.nanoTime();

                    if (i > 0) {
                        prop.put("direct.detailed.timing.ignore", null);
                    }
                }

                VirtualSocket vs = createClientSocket(m, target,
                        partialTimeout, prop);

                if (timing != null) {
                    timing[1 + i] = System.nanoTime() - timing[1 + i];
                }

                if (vs != null) {
                    if (notSuitableCount > 0) {
                        // We managed to connect, but not with the first module,
                        // so
                        // we remember this to speed up later connections.
                        clusters.succes(target, m);
                    }
                    return vs;
                }

                if (timeout > 0 && i < order.length - 1) {
                    timeLeft -= System.currentTimeMillis() - start;

                    if (timeLeft <= 0) {
                        // TODO can this happen ?
                        partialTimeout = 1000;
                    } else {
                        partialTimeout = (timeLeft / (order.length - (i + 1)));
                    }
                }

                notSuitableCount++;
            }

            if (notSuitableCount == order.length) {
                if (logger.isInfoEnabled()) {
                    logger.info("No suitable module found to connect to "
                            + target);
                }

                // No suitable modules found...
                throw new ConnectException(
                        "No suitable module found to connect to " + target);
            } else {
                // Apparently, some modules where suitable but failed to
                // connect.
                // This is treated as a timeout
                if (logger.isInfoEnabled()) {
                    logger.info("None of the modules could to connect to "
                            + target);
                }

                // TODO: is this right ?
                throw new SocketTimeoutException("Timeout during connect to "
                        + target);
            }

        } finally {
            if (timing != null) {
                timing[0] = System.nanoTime() - timing[0];
                prop.remove("direct.detailed.timing.ignore");
            }
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

    public VirtualServerSocket createServerSocket(int port, int backlog,
            boolean retry, Map properties) {

        VirtualServerSocket result = null;

        while (result == null) {
            try {
                result = createServerSocket(port, backlog, null);
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
                    backlog, properties);

            serverSockets.put(port, vss);
            return vss;
        }
    }

    // TODO: hide this thing ?
    public ServiceLink getServiceLink() {
        return serviceLink;
    }

    public DirectSocketAddress getLocalHost() {
        return myAddresses;
    }

    public String getLocalCluster() {
        return clusters.localCluster();
    }

    public DirectSocketAddress getLocalProxy() {
        return hubAddress;
    }

    protected void closed(int port) {
        synchronized (serverSockets) {
            serverSockets.remove(new Integer(port));
        }
    }

    public static synchronized VirtualSocketFactory getSocketFactory(String name) {
        return factories.get(name);
    }

    public static synchronized VirtualSocketFactory getOrCreateSocketFactory(
            String name, java.util.Properties p, boolean addDefaults)
            throws InitializationException {
        VirtualSocketFactory result = factories.get(name);

        if (result == null) {
            result = createSocketFactory(p, addDefaults);
            factories.put(name, result);
        } else if (!p.equals(result.properties)) {
            throw new InitializationException(
                    "could not retrieve existing factory, properties are not equal");

        }

        return result;
    }

    public static synchronized void registerSocketFactory(String name,
            VirtualSocketFactory factory) {
        factories.put(name, factory);
    }

    public static synchronized VirtualSocketFactory getDefaultSocketFactory()
            throws InitializationException {

        if (defaultFactory == null) {
            defaultFactory = createSocketFactory();
        }

        return defaultFactory;
    }

    public static VirtualSocketFactory createSocketFactory()
            throws InitializationException {

        return createSocketFactory(null, true);
    }

    public static VirtualSocketFactory createSocketFactory(Map p,
            boolean addDefaults) throws InitializationException {
        return createSocketFactory(new TypedProperties(p), addDefaults);
    }

    public static VirtualSocketFactory createSocketFactory(
            java.util.Properties properties, boolean addDefaults)
            throws InitializationException {

        TypedProperties typedProperties = new TypedProperties();

        if (addDefaults) {
            typedProperties.putAll(Properties.getDefaultProperties());
        }

        if (properties != null) {
            typedProperties.putAll(properties);
        }

        VirtualSocketFactory factory = new VirtualSocketFactory(typedProperties);

        if (typedProperties.containsKey("smartsockets.factory.statistics")) {

            int tmp = typedProperties.getIntProperty(Properties.STATISTICS_INTERVAL,
                    0);

            if (tmp > 0) {

                if (tmp < 1000) {
                    tmp *= 1000;
                }

                if (printer == null) {
                    printer = new StatisticsPrinter(tmp);
                    ThreadPool.createNew(printer,
                            "SmartSockets Statistics Printer");
                } else {
                    printer.adjustInterval(tmp);
                }
            }
        }

        return factory;
    }

    public VirtualSocket createBrokeredSocket(InputStream brokered_in,
            OutputStream brokered_out, boolean b, Map p) {
        throw new RuntimeException("createBrokeredSocket not implemented");
    }

    public VirtualSocketAddress getLocalVirtual() {
        return localVirtualAddress;
    }

    public String getVirtualAddressAsString() {
        return localVirtualAddressAsString;
    }

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
