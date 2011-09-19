package ibis.smartsockets.naming;

import ibis.smartsockets.util.MalformedAddressException;
import ibis.smartsockets.util.ThreadPool;
import ibis.smartsockets.virtual.InitializationException;
import ibis.smartsockets.virtual.VirtualSocketAddress;
import ibis.smartsockets.virtual.VirtualSocketFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class presents a resolver interface which can be used to register
 * and resolve names to smart socket addresses.
 *
 * @author nick <palmer@cs.vu.nl>
 *
 */
public final class NameResolver {
    /** The logger used by this class. */
    private static final Logger LOGGER = LoggerFactory
            .getLogger("ibis.smartsockets.naming");

    /**
     * The resolver methods we know about.
     */
    private static Map<String, NameResolver>sResolvers =
            new HashMap<String, NameResolver>();

    /**
     * The clients listening for "local" discoveries.
     */
    private ArrayList<LocalNamingListener> mListeners =
            new ArrayList<LocalNamingListener>();

    /**
     * The VirtualSocketFactory we are using to communicate.
     */
    private VirtualSocketFactory mFactory;

    /**
     * The interface used to inform clients of a new local name
     * being discovered. What is "local" depends on the methods
     * which are running within the resolver.
     * @author nick
     *
     */
    public static interface LocalNamingListener {
        /**
         * Called when a "local" name is discovered by the resolver.
         * @param name the discovered name
         */
        void onLocalNameDiscovered(String name);

        /**
         * Called when a "local" name is removed by the resolver.
         * @param name the removed name
         */
        void onLocalNameRemoved(String name);
    }

    /**
     * Represents a name record in the system.
     * @author nick <palmer@cs.vu.nl>
     *
     */
    private static class NameRecord {

        /** Construct a new name record.
         * @param name the name of the socket
         * @param address the address of the socket
         * @param data application specific data for the service
         */
        NameRecord(final String name, final VirtualSocketAddress address,
                final Map<String, String>data) {
            mAddress = address;
            mSocketInfo = data;
        }

        /**
         * The address for this record.
         */
        private final VirtualSocketAddress mAddress;

        /**
         * The app specific info for this record.
         */
        private final Map<String, String> mSocketInfo;
    }

    /* TODO: Combine the following three data structures into one
     * using flags on the NameRecord and add TTL values to the
     * registered names. Then add a GC thread
     * which expires names which are beyond the TTL that we are not
     * authoritative for and re-registers names that we are authoritative
     * for shortly before they are set to expire.
     */

    /**
     * The names that have been discovered.
     */
    private Map<String, NameRecord>mNames = new HashMap<String, NameRecord>();

    /**
     * The names that are "local" names
     */
    private List<String> mLocalNames = new ArrayList<String>();

    /**
     * The names for which we are an authority
     */
    private List<String> mAuthorityFor = new ArrayList<String>();

    /**
     * The resolver methods we know about.
     */
    private List<NameResolverMethod> mResolverMethods;

    /** Constructs a new name resolver.
     *
     * @param factory the socket factory to use
     * @throws InitializationException if something goes wrong
     */
    private NameResolver(final String name, final VirtualSocketFactory factory)
            throws InitializationException {
        mFactory = factory;
        mResolverMethods = NameResolverMethod.initMethodsForResolver(this);
        if (mResolverMethods.size() == 0) {
            throw new InitializationException(
                    "Unable to initialize any resolver methods.");
        }
    }

    /**
     * Closes all running resolvers and their factories
     */
    public static void closeAllResolvers() {
        synchronized (sResolvers) {
            for (NameResolver resolver : sResolvers.values()) {
                resolver.close();
            }
            sResolvers.clear();
        }
    }

    private void close() {
        for (NameResolverMethod method : mResolverMethods) {
            method.stop();
        }
        mListeners.clear();
        mNames.clear();
        mFactory.end();
    }

    /**
     * Returns or constructs a resolver with the given name and properties.
     *
     * @param name The name of the resolver
     * @param props Any properties used to construct the
     *      VirtualSocketFactory underlying this resolver
     * @param addDefaults true if we should add the default
     *      smart socket properties to those given
     * @return The requested NameResolver instance
     * @throws InitializationException if the socket factory
     *      could not be constructed
     */
    public static NameResolver getOrCreateResolver(final String name,
            Properties props, boolean addDefaults)
                    throws InitializationException {
        NameResolver resolver;
        synchronized (sResolvers) {
            if (sResolvers.containsKey(name)) {
                resolver = sResolvers.get(name);
            } else {
                VirtualSocketFactory factory = VirtualSocketFactory.getOrCreateSocketFactory(name, props, addDefaults);
                resolver = new NameResolver(name, factory);
                sResolvers.put(name, resolver);
            }
        }
        return resolver;
    }

    /**
     * Returns the address for a given name if it is known immediately.
     * @param name The name to be resolved
     * @return the address for the requested name or null
     */
    public VirtualSocketAddress resolve(final String name) {
        return resolve(name, -1);
    }

    /**
     * Returns the address for a given name.
     * @param name The name to be resolved
     * @param timeout The maximum time to wait for resolution
     * @return The address for the requested name or null
     */
    public VirtualSocketAddress resolve(final String name, final int timeout) {
        VirtualSocketAddress address = null;
        long finish = System.currentTimeMillis() + timeout;
        synchronized (mNames) {
            while (address == null) {
                LOGGER.debug("Attempting to resolve: {}", name);
                if (mNames.containsKey(name)) {
                    NameRecord record = mNames.get(name);
                    address = record.mAddress;
                    LOGGER.debug("Resolved: {} {}", name, address);
                } else {
                    // Spawn threads to handle the query with each method.
                    for (final NameResolverMethod method : mResolverMethods) {
                        ThreadPool.createNew(new Runnable() {
                            @Override
                            public void run() {
                                method.query(name);
                            }
                        }, "query " + name + " " + method);
                    }
                    long now = System.currentTimeMillis();
                    if (timeout > 0 && now < finish) {
                        try {
                            mNames.wait(finish - now);
                        } catch (InterruptedException e) {
                            // Ignored
                            LOGGER.error(
                                    "Got interrupted exception "
                            + "waiting for resolution.", e);
                        }
                    } else {
                        LOGGER.debug("Timeout. Unable to resolve: {}", name);
                        break;
                    }
                }
            }
        }
        return address;
    }

    /**
     * @return the socket factory being used by this resolver
     */
    public VirtualSocketFactory getSocketFactory() {
        return mFactory;
    }

    /**
     * Removes a registration from the system.
     * @param socketName The name of the socket to remove
     */
    public void unregister(final String socketName) {
        synchronized (mNames) {
            mNames.remove(socketName);
            mLocalNames.remove(socketName);
            mAuthorityFor.remove(socketName);
            for (NameResolverMethod method : mResolverMethods) {
                try {
                    method.unregister(socketName);
                } catch (IOException e) {
                    LOGGER.error("Exception while unregistering with " + method, e);
                }
            }
            mNames.notifyAll();
        }
    }

    /**
     * Returns true if this resolver has the requested name.
     * @param socketName the name of the desired socket
     * @return true if we have this socket name already cached.
     */
    public boolean hasName(final String socketName) {
        synchronized (mNames) {
            return mNames.containsKey(socketName);
        }
    }

    /**
     * Returns true if this resolver is authoritative for the requested name.
     * @param socketName the name of the desired socket
     * @return true if we have this socket name already cached.
     */
    public boolean hasLocalName(final String socketName) {
        synchronized (mNames) {
            return mLocalNames.contains(socketName);
        }
    }

    public boolean isAuthorityFor(final String socketName) {
        synchronized (mNames) {
            return mAuthorityFor.contains(socketName);
        }
    }

    /**
     * Registers a socket with the system.
     * @param socketName The name of the socket
     * @param address The address the name resolves to
     * @param serviceInfo Any additional application
     *      specific information associated with this name
     */
    public void register(final String socketName,
            final VirtualSocketAddress address,
            final Map<String, String> serviceInfo) {
        synchronized (mNames) {
            mNames.put(socketName,
                    new NameRecord(socketName, address, serviceInfo));
            mLocalNames.add(socketName);
            mAuthorityFor.add(socketName);
            for (NameResolverMethod method : mResolverMethods) {
                try {
                    method.register(socketName, address.toString(), serviceInfo);
                } catch (IOException e) {
                    LOGGER.error("Exception while registering with " + method, e);
                }
            }
            mNames.notifyAll();
        }
    }

    /**
     * Returns any application specific information provided at registration.
     * @param serviceName The name to get information about
     * @param timeout The maximum time to wait for resolution
     * @return the application specific information or null
     */
    public Map<String, String> resolveInfo(final String serviceName,
            final int timeout) {
        synchronized (mNames) {
            // Make sure the name resolves
            if (resolve(serviceName, timeout) != null) {
                return mNames.get(serviceName).mSocketInfo;
            }
        }
        return null;
    }

    /**
     * Returns any application specific information provided at registration.
     * @param serviceName The name to get information about
     * @return the application specific information or null
     */
    public Map<String, String> resolveInfo(final String serviceName) {
        return resolveInfo(serviceName, -1);
    }

    /**
     * Registers a listener which gets called when a "local" name is found.
     * @param listener The listener to register
     */
    public void registerLocalDiscoveryListener(
            final LocalNamingListener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes a listener which was previously registered.
     * @param listener The listener to remove
     */
    public void unRegisterLocalDiscoveryListener(
            final LocalNamingListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Fires a local add event to all listeners
     * @param local true if we should fire the event to local listeners
     * @param name The added name
     * @param address The added address
     * @param props The app specific properties
     * @throws MalformedAddressException if the address is malformed
     * @throws UnknownHostException if the host is unknown
     */
    void handleAdd(boolean local, String name, String address,
            Map<String, String>props)
            throws UnknownHostException, MalformedAddressException {
        synchronized (mNames) {
            mNames.put(name, new NameRecord(name,
                    new VirtualSocketAddress(address), props));
            if (local) {
                mLocalNames.add(name);
            }
            mNames.notifyAll();
        }
        if (local) {
            for (LocalNamingListener listener : mListeners) {
                listener.onLocalNameDiscovered(name);
            }
        }
    }

    /**
     * Fires a local remove event to all listeners
     * @param local true if we should fire the event to local listeners
     * @param name the name which was removed
     */
    void handleRemove(boolean local, String name) {
        synchronized (mNames) {
            mNames.remove(name);
            mLocalNames.remove(name);
            mAuthorityFor.remove(name);
            mNames.notifyAll();
        }
        if (local) {
            for (LocalNamingListener listener : mListeners) {
                listener.onLocalNameRemoved(name);
            }
        }
    }

    /**
     * Returns all names which are considered "local"
     * @return the list of local names.
     */
    List<String> getLocallyRegisteredNames() {
        List<String> localNames = new ArrayList<String>();
        synchronized (mNames) {
            for (int i = 0; i < mLocalNames.size(); i++) {
                localNames.add(mLocalNames.get(i));
            }
        }
        return localNames;
    }
}
