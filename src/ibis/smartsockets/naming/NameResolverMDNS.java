package ibis.smartsockets.naming;

import ibis.smartsockets.util.MalformedAddressException;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for handling naming using multicast DNS.
 * @author nick <palmer@cs.vu.nl>
 *
 */
public class NameResolverMDNS extends NameResolverMethod {
    /** The logger we use. **/
    private static final Logger LOGGER = LoggerFactory
            .getLogger("ibis.smartsockets.naming.mdns");

    // Register us with the methods
    static {
        NameResolverMethod.registerMethod(NameResolverMDNS.class);
    }

    /** Note: This must end in a period or discovery will not work. */
    private static final String SMARTSOCKETS_NAMING_SERVICE =
            "_smartsockets_naming._tcp.local.";

    /** The socket name. **/
    private static final String SOCKET_NAME = "ssName";

    /** The socket address. **/
    private static final String SOCKET_ADDRESS = "ssAddress";

    /**
     * The JmDNS adapter we use to register things.
     */
    private JmDNS mJmDNS;

    /**
     * The names we have registered.
     */
    private Map<String, ServiceInfo> mLocalNames =
            new HashMap<String, ServiceInfo>();

    /**
     * The service listener that handles callbacks from JmDNS.
     */
    private ServiceListener mServiceListener = new ServiceListener() {

        @Override
        public void serviceAdded(final ServiceEvent event) {
            // Ignored until it resolves
            LOGGER.debug("Got service added: {} {}", event.getType(),
                    event.getName());
            mJmDNS.requestServiceInfo(event.getType(), event.getName(), true);
        }

        @Override
        public void serviceRemoved(final ServiceEvent event) {
            LOGGER.debug("Got service removed: {}", event.getInfo());
            fireLocalRemove(event.getInfo().getName());
        }

        @Override
        public void serviceResolved(final ServiceEvent event) {
            LOGGER.debug("Got service resolved: {}", event);
            try {
                fireLocalAdd(
                        event.getInfo().getPropertyString(SOCKET_NAME),
                        event.getInfo().getPropertyString(SOCKET_ADDRESS),
                        getEventProperties(event.getInfo()));
            } catch (MalformedAddressException e) {
                LOGGER.error("Error handling service resolved.", e);
            } catch (UnknownHostException e) {
                LOGGER.error("Unknown host handling service resolved.", e);
            }
        }


        /**
         * Returns a hash with the app specific properties of the event.
         * @param info the event to extract from
         * @return The app specific properties as a Map
         */
        private Map<String, String> getEventProperties(ServiceInfo info) {
            Map<String, String>props = new HashMap<String, String>();
            Enumeration<String> propertyNames = info.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String property = propertyNames.nextElement();
                if (!property.equals(SOCKET_NAME) &&
                        !property.equals(SOCKET_ADDRESS)) {
                    props.put(property, info.getPropertyString(property));
                }
            }
            return props;
        }
    };

    @Override
    public final void register(final String name, final String address,
            final Map<String, String> info) throws IOException {
        if (mJmDNS != null) {
            synchronized (mLocalNames) {
                LOGGER.debug("Registering: {} {}", name, address);
                if (mLocalNames.containsKey(name)) {
                    unregister(name);
                }
                ServiceInfo service = makeServiceInfo(name, address, info);
                mJmDNS.registerService(service);
                mLocalNames.put(name, service);
            }
        }
    }

    @Override
    public final void start() throws IOException {
        LOGGER.debug("Starting JmDNS service for resolver.");
        mJmDNS = JmDNS.create();
        mJmDNS.addServiceListener(SMARTSOCKETS_NAMING_SERVICE,
                mServiceListener);
        LOGGER.debug("MDNS based naming service started.");
    }

    @Override
    public final void stop() {
        LOGGER.debug("Stopping mDNS discovery.");
        if (mJmDNS != null) {
            try {
                LOGGER.info("Unregistring all services.");
                synchronized (mLocalNames) {
                    for (Entry<String, ServiceInfo> info
                        : mLocalNames.entrySet()) {
                        LOGGER.debug("Unregistering: {}",
                                info.getValue().getName());
                        mJmDNS.unregisterService(info.getValue());
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Got exception while unregistering with mDNS", e);
            }
            try {
                mJmDNS.close();
            } catch (IOException e) {
                LOGGER.error("Got exception while closing mDNS", e);
            }
        }
        mJmDNS = null;
    }

    @Override
    public final void unregister(final String socketName) {
        if (mJmDNS != null) {
            synchronized (mLocalNames) {
                if (mLocalNames.containsKey(socketName)) {
                    ServiceInfo info = mLocalNames.remove(socketName);
                    LOGGER.debug("Unregistering: " + info.getName());
                    mJmDNS.unregisterService(info);
                }
            }
        }
    }

    /**
     * Makes a ServiceInfo object from the given parameters.
     * @param name The name of the service
     * @param address The VirtualSocketAddress for the service
     * @param info Any application specific information
     * @return the ServiceInfo object representing the service.
     */
    private ServiceInfo makeServiceInfo(final String name, final String address,
            Map<String, String>info) {
        if (info == null) {
            info = new HashMap<String, String>();
        } else if (info.containsKey(SOCKET_NAME) || info.containsKey(SOCKET_ADDRESS)) {
            throw new IllegalArgumentException("Info hash can not contain: "
                    + SOCKET_NAME + " or " + SOCKET_ADDRESS);
        }

        info.put(SOCKET_NAME, name);
        info.put(SOCKET_ADDRESS, address);
        return ServiceInfo.create(SMARTSOCKETS_NAMING_SERVICE, name, 0, 0, 0,
                true, info);
    }

    @Override
    public void query(String name) {
        // MDNS doesn't do querying
    }

}
