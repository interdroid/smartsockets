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
import ibis.util.ThreadPool;

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

import org.apache.log4j.Logger;

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
        private VirtualSocketFactory factory;
        private String prefix;
        
        StatisticsPrinter(VirtualSocketFactory factory, int timeout, 
                String prefix) {
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
                } catch (Exception e) {
                    // ignore
                }
                
                t = getTimeout();
                
                if (t > 0) { 
                    try {
                        factory.printStatistics(prefix);
                    } catch (Exception e) {
                        // TODO: IGNORE ?
                    }
                }                
            }
        }

        private synchronized int getTimeout() {
            return timeout;
        }

        public synchronized void adjustInterval(int interval) {
            timeout = interval;
            notifyAll();
        }
    }

    private static final Map<String, VirtualSocketFactory> factories = new HashMap<String, VirtualSocketFactory>();

    private static VirtualSocketFactory defaultFactory = null;

    protected static final Logger logger = Logger
            .getLogger("ibis.smartsockets.virtual.misc");

    protected static final Logger conlogger = Logger
            .getLogger("ibis.smartsockets.virtual.connect");

    private static final Logger statslogger = Logger
            .getLogger("ibis.smartsockets.statistics");

    private final DirectSocketFactory directSocketFactory;

    private final ArrayList<ConnectModule> modules = new ArrayList<ConnectModule>();

    private ConnectModule direct;

    private final TypedProperties properties;

    private final int DEFAULT_BACKLOG;

    private final int DEFAULT_TIMEOUT;

    private final int DEFAULT_ACCEPT_TIMEOUT;

    private final boolean DETAILED_EXCEPTIONS;

    private final Random random;

    private final HashMap<Integer, VirtualServerSocket> serverSockets = new HashMap<Integer, VirtualServerSocket>();

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
        
        printStatistics = p.booleanProperty(
                SmartSocketsProperties.STATISTICS_PRINT);

        if (printStatistics) {             
            statisticPrefix = p.getProperty(
                    SmartSocketsProperties.STATISTICS_PREFIX, "SmartSockets");
        
            int tmp = p.getIntProperty(
                    SmartSocketsProperties.STATISTICS_INTERVAL, 0);
            
            if (tmp > 0) {
                printer = new StatisticsPrinter(this, tmp*1000, statisticPrefix);
                ThreadPool.createNew(printer, "SmartSockets Statistics Printer");
            }
        }
    }

    private void startHub(TypedProperties p) throws InitializationException {

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
        
        
        
        try {
            serviceLink = ServiceLink.getServiceLink(properties, hubs,
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

    private void loadModule(String name) throws Exception {

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

        int timeout = p.getIntProperty(SmartSocketsProperties.CONNECT_TIMEOUT, -1);

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

    public static void close(VirtualSocket s, OutputStream out, InputStream in) {

        try {
            if (out != null) {
                out.close();
            }
        } catch (Throwable e) {
            // ignore
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (Throwable e) {
            // ignore
        }

        try {
            if (s != null) {
                s.close();
            }
        } catch (Throwable e) {
            // ignore
        }
    }

    public static void close(VirtualSocket s, SocketChannel channel) {

        if (channel != null) {
            try {
                channel.close();
            } catch (Exception e) {
                // ignore
            }
        }

        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    // This method implements a connect using a specific module. This method 
    // will return null when: 
    //
    //  - runtime requirements do not match
    //  - the module throws a NonFatalIOException 
    //
    // When a connection is succesfully establed, we wait for a accept from the 
    // remote. There are three possible outcomes: 
    //
    //  - the connection is accepted in time 
    //  - the connection is not accepted in time or the connection is lost
    //  - the remote side is overloaded and closes the connection
    //
    // In the first case the newly created socket is returned and in the second 
    // case an exception is thrown. In the last case the connection will be 
    // retried using a backoff mechanism until 'timeleft' (the total timeout  
    // specified by the user) is spend. Each new try behaves exactly the same as 
    // the previous attempts.    
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

                    // System.err.println("Backoff = " + backoff + " Leftover = " + leftover + " Sleep time = " + sleeptime);

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

    // This method loops over and array of connection modules, trying to setup 
    // a connection with each of them each in turn. Returns when:
    //   - a connection is established
    //   - a non-transient error occurs (i.e. remote port not found)
    //   - all modules have failed
    //   - a timeout occurred    
    //
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
                 if (timing != null) {
                 timing[1 + i] = System.nanoTime();

                 if (i > 0) {
                 prop.put("direct.detailed.timing.ignore", null);
                 }
                 }*/

                try {
                    vs = createClientSocket(m, target, timeout, timeLeft,
                            fillTimeout, prop);
                } catch (NonFatalIOException e) {
                    // Store the exeception and continue with the next module
                    exceptions[i] = e;
                }

                /*
                 if (timing != null) {
                 timing[1 + i] = System.nanoTime() - timing[1 + i];
                 }*/

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
            if (timing != null) {
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

    public VirtualSocket createClientSocket(VirtualSocketAddress target,
            int timeout, Map<String, Object> prop) throws IOException {
        return createClientSocket(target, timeout, false, prop);
    }

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
         long [] timing = null;

         if (prop != null) {
         // Note: it's up to the user to ensure that this thing is large 
         // enough! i.e., it should be of size 1+modules.length                
         timing = (long[]) prop.get("virtual.detailed.timing");

         if (timing != null) {
         timing[0] = System.nanoTime();
         }
         }
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
                /*timing*/null, fillTimeout, prop);
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

    public VirtualServerSocket createServerSocket(Map<String, Object> properties)
            throws IOException {
        return new VirtualServerSocket(this, DEFAULT_ACCEPT_TIMEOUT, properties);
    }

    protected void bindServerSocket(VirtualServerSocket vss, int port)
            throws BindException {

        synchronized (serverSockets) {
            if (serverSockets.containsKey(port)) {
                throw new BindException("Port " + port + " already in use!");
            }

            serverSockets.put(port, vss);
        }
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
                    backlog, DEFAULT_ACCEPT_TIMEOUT, properties);

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

    public DirectSocketAddress getLocalHub() {
        return hubAddress;
    }

    public void addHubs(DirectSocketAddress... hubs) {
        if (hub != null) {
            hub.addHubs(hubs);
        } else if (serviceLink != null) {
            serviceLink.addHubs(hubs);
        }
    }

    public void addHubs(String... hubs) {
        if (hub != null) {
            hub.addHubs(hubs);
        } else if (serviceLink != null) {
            serviceLink.addHubs(hubs);
        }
    }
    
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

    public void end() {
        
        if (printer != null) { 
            printer.adjustInterval(-1);                    
        } 
        
        if (printStatistics) {  
            printStatistics(statisticPrefix + " [EXIT]");
        }
        
        if (hub != null) {
            hub.end();
        }
    }

    protected void closed(int port) {
        synchronized (serverSockets) {
            serverSockets.remove(Integer.valueOf(port));
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
            typedProperties.putAll(SmartSocketsProperties
                    .getDefaultProperties());
        }

        if (properties != null) {
            typedProperties.putAll(properties);
        }

        VirtualSocketFactory factory = new VirtualSocketFactory(
                DirectSocketFactory.getSocketFactory(typedProperties),
                typedProperties);
        
        return factory;
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
